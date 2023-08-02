package cn.ichensw.neroapiadmin.service.impl.inner;

import cn.ichensw.neroapiadmin.exception.BusinessException;
import cn.ichensw.neroapiadmin.service.UserInterfaceInfoService;
import cn.ichensw.neroapiadmin.service.UserService;
import cn.ichensw.neroapicommon.common.ErrorCode;
import cn.ichensw.neroapicommon.model.entity.UserInterfaceInfo;
import cn.ichensw.neroapicommon.service.InnerUserInterfaceInfoService;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;

/**
 * @author csw
 * @description 针对表【user_interface_info(用户调用接口关系)】的数据库操作Service实现
 * @createDate 2023-06-17 12:32:57
 */
@DubboService
public class InnerUserInterfaceInfoServiceImpl implements InnerUserInterfaceInfoService {

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Resource
    private UserService userService;

    @Override
    public boolean invokeCount(long interfaceInfoId, long userId) {
        // 查询接口是否存在
        UserInterfaceInfo userInterfaceInfo = userInterfaceInfoService.lambdaQuery()
                .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                .eq(UserInterfaceInfo::getUserId, userId)
                .one();
        if (userInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "接口不存在");
        }
        // 修改调用次数
        return userInterfaceInfoService.lambdaUpdate()
                .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                .eq(UserInterfaceInfo::getUserId, userId)
                .set(UserInterfaceInfo::getTotalNum, userInterfaceInfo.getTotalNum() + 1)
                .set(UserInterfaceInfo::getLeftNum, userInterfaceInfo.getLeftNum() - 1)
                .update();
    }

    @Override
    public UserInterfaceInfo hasLeftNum(Long interfaceId, Long userId) {
        return userInterfaceInfoService.lambdaQuery()
                .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceId)
                .eq(UserInterfaceInfo::getUserId, userId)
                .one();
    }

    @Override
    public Boolean addDefaultUserInterfaceInfo(Long interfaceId, Long userId) {
        UserInterfaceInfo userInterfaceInfo = new UserInterfaceInfo();
        userInterfaceInfo.setUserId(userId);
        userInterfaceInfo.setInterfaceInfoId(interfaceId);
        userInterfaceInfo.setLeftNum(99999999);

        return userInterfaceInfoService.save(userInterfaceInfo);
    }

    @Override
    public UserInterfaceInfo checkUserHasInterface(long interfaceId, long userId) {
        if (interfaceId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return userInterfaceInfoService.lambdaQuery()
                .eq(UserInterfaceInfo::getUserId, userId)
                .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceId)
                .one();
    }
}




