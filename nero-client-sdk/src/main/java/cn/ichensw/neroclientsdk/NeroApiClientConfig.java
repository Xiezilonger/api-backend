package cn.ichensw.neroclientsdk;

import cn.ichensw.neroclientsdk.client.NeroApiClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Nero API 客户端配置类
 * @author csw
 */
@Data
@Configuration
@ConfigurationProperties("nero.client")
@ComponentScan
public class NeroApiClientConfig {

    private String accessKey;

    private String secretKey;

    /**
     * 此处方法取名无所谓的，不影响任何地方
     *
     * @return
     */
    @Bean
    public NeroApiClient getApiClient() {
        return new NeroApiClient(accessKey, secretKey);
    }
}
