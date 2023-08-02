package cn.ichensw.neroapigateway.filter;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.URLUtil;
import cn.ichensw.neroapicommon.common.ErrorCode;
import cn.ichensw.neroapicommon.model.entity.InterfaceInfo;
import cn.ichensw.neroapicommon.model.entity.User;
import cn.ichensw.neroapicommon.model.entity.UserInterfaceInfo;
import cn.ichensw.neroapicommon.service.InnerInterfaceInfoService;
import cn.ichensw.neroapicommon.service.InnerUserInterfaceInfoService;
import cn.ichensw.neroapicommon.service.InnerUserService;
import cn.ichensw.neroapigateway.exception.BusinessException;
import cn.ichensw.neroclientsdk.utils.SignUtils;
import jodd.util.StringUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.reactivestreams.Publisher;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author csw
 */
@Component
@Slf4j
@Data
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    public static final List<String> IP_WHITE_LIST = Collections.singletonList("127.0.0.1");
    private static final String DYE_DATA_HEADER = "X-Dye-Data";
    private static final String DYE_DATA_VALUE = "nero";

    @DubboReference
    private InnerUserService innerUserService;
    @DubboReference
    private InnerUserInterfaceInfoService innerUserInterfaceInfoService;
    @DubboReference
    private InnerInterfaceInfoService innerInterfaceInfoService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 请求日志
        ServerHttpRequest request = exchange.getRequest();
        String IP_ADDRESS = Objects.requireNonNull(request.getLocalAddress()).getHostString();
        String path = request.getPath().value();
        log.info("请求唯一标识：{}", request.getId());
        log.info("请求路径：{}", path);
        log.info("请求参数：{}", request.getQueryParams());
        log.info("请求来源地址：{}", IP_ADDRESS);
        log.info("请求来源地址：{}", request.getRemoteAddress());

        ServerHttpResponse response = exchange.getResponse();

        // 2. 黑白名单
        if (!IP_WHITE_LIST.contains(IP_ADDRESS)) {
            return handleNoAuth(response);
        }
        // 3. 用户鉴权 （判断 accessKey 和 secretKey 是否合法）
        HttpHeaders headers = request.getHeaders();
        String accessKey = headers.getFirst("accessKey");
        String timestamp = headers.getFirst("timestamp");
        String nonce = headers.getFirst("nonce");
        String sign = headers.getFirst("sign");
        String body = URLUtil.decode(headers.getFirst("body"), CharsetUtil.CHARSET_UTF_8);
        String method = headers.getFirst("method");

        if (StringUtil.isEmpty(nonce)
                || StringUtil.isEmpty(sign)
                || StringUtil.isEmpty(timestamp)
                || StringUtil.isEmpty(method)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "请求头参数不完整！");
        }

        // 通过 accessKey 查询是否存在该用户
        User invokeUser = innerUserService.getInvokeUser(accessKey);
        if (invokeUser == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "accessKey 不合法！");
        }
        // 判断随机数是否存在，防止重放攻击
        String existNonce = (String) redisTemplate.opsForValue().get(nonce);
        if (StringUtil.isNotBlank(existNonce)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "请求重复！");
        }
        // 时间戳 和 当前时间不能超过 5 分钟 (300000毫秒)
        long currentTimeMillis = System.currentTimeMillis() / 1000;
        long difference = currentTimeMillis - Long.parseLong(timestamp);
        if (Math.abs(difference) > 300000) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "请求超时！");
        }
        // 校验签名
        // 应该通过 accessKey 查询数据库中的 secretKey 生成 sign 和前端传递的 sign 对比
        String serverSign = SignUtils.genSign(body, invokeUser.getSecretKey());
        if (!sign.equals(serverSign)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "签名错误！");
        }

        // 4. 请求的模拟接口是否存在？
        // 从数据库中查询接口是否存在，以及方法是否匹配（还有请求参数是否正确）
        InterfaceInfo interfaceInfo = null;
        try {
            interfaceInfo = innerInterfaceInfoService.getInterfaceInfo(path, method);
        } catch (Exception e) {
            log.error("getInvokeInterface error", e);
        }
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "接口不存在！");
        }

        // 5. 请求转发，调用模拟接口
        // 6. 响应日志
        return handleResponse(exchange, chain, interfaceInfo.getId(), invokeUser.getId());
    }

    @Override
    public int getOrder() {
        return -1;
    }

    /**
     * 响应没权限
     *
     * @param response
     * @return
     */
    private Mono<Void> handleNoAuth(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.setRawStatusCode(HttpStatus.FORBIDDEN.value());
        return response.setComplete();
    }

    /**
     * 处理响应
     *
     * @param exchange
     * @param chain
     * @return
     */
    private Mono<Void> handleResponse(ServerWebExchange exchange, GatewayFilterChain chain, long interfaceInfoId, long userId) {
        try {
            ServerHttpResponse originalResponse = exchange.getResponse();
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();

            HttpStatus statusCode = originalResponse.getStatusCode();

            if (statusCode == HttpStatus.OK) {
                ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                    @Override
                    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                        if (body instanceof Flux) {
                            Flux<? extends DataBuffer> fluxBody = Flux.from(body);
                            return super.writeWith(
                                    fluxBody.map(dataBuffer -> {
                                        // 7. 调用成功，接口调用次数 + 1 invokeCount
                                        try {
                                            postHandler(exchange.getRequest(), exchange.getResponse(), interfaceInfoId, userId);
                                        } catch (Exception e) {
                                            log.error("invokeCount error", e);
                                            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "接口调用次数 + 1 失败！");
                                        }
                                        byte[] content = new byte[dataBuffer.readableByteCount()];
                                        dataBuffer.read(content);
                                        DataBufferUtils.release(dataBuffer);//释放掉内存
                                        // 构建日志
                                        StringBuilder sb2 = new StringBuilder(200);
                                        List<Object> rspArgs = new ArrayList<>();
                                        rspArgs.add(originalResponse.getStatusCode());
                                        String data = new String(content, StandardCharsets.UTF_8); //data
                                        sb2.append(data);
                                        // 打印日志
                                        log.info("响应结果：" + data);
                                        return bufferFactory.wrap(content);
                                    })
                            );
                        } else {
                            // 8. 调用失败，返回规范的错误码
                            log.error("<--- {} 响应code异常", getStatusCode());
                        }
                        return super.writeWith(body);
                    }
                };

                // 流量染色，只有染色数据才能被调用
                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                        .header(DYE_DATA_HEADER, DYE_DATA_VALUE)
                        .build();

                ServerWebExchange serverWebExchange = exchange.mutate()
                        .request(modifiedRequest)
                        .response(decoratedResponse)
                        .build();
                return chain.filter(serverWebExchange);
            }
            //降级处理返回数据
            return chain.filter(exchange);
        } catch (Exception e) {
            log.error("网关处理异常响应.\n" + e);
            return chain.filter(exchange);
        }
    }

    private void postHandler(ServerHttpRequest request, ServerHttpResponse response, Long interfaceInfoId, Long userId) {
        RLock lock = redissonClient.getLock("api:add_interface_num");
        if (response.getStatusCode() == HttpStatus.OK) {
            CompletableFuture.runAsync(() -> {
                if (lock.tryLock()) {
                    try {
                        addInterfaceNum(request, interfaceInfoId, userId);
                    } finally {
                        lock.unlock();
                    }
                }
            });
        }
    }

    private void addInterfaceNum(ServerHttpRequest request, Long interfaceInfoId, Long userId) {
        String nonce = request.getHeaders().getFirst("nonce");
        if (StringUtil.isEmpty(nonce)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "请求重复");
        }
        UserInterfaceInfo userInterfaceInfo = innerUserInterfaceInfoService.hasLeftNum(interfaceInfoId, userId);
        // 接口未绑定用户
        if (userInterfaceInfo == null) {
            Boolean save = innerUserInterfaceInfoService.addDefaultUserInterfaceInfo(interfaceInfoId, userId);
            if (save == null || !save) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "接口绑定用户失败！");
            }
        }
        if (userInterfaceInfo != null && userInterfaceInfo.getLeftNum() <= 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "调用次数已用完！");
        }
        redisTemplate.opsForValue().set(nonce, 1, 5, TimeUnit.MINUTES);
        innerUserInterfaceInfoService.invokeCount(interfaceInfoId, userId);
    }
}
