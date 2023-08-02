package cn.ichensw.neroapiadmin.service.impl.inner;

import cn.ichensw.neroapiadmin.service.InterfaceInfoService;
import cn.ichensw.neroapicommon.model.entity.InterfaceInfo;
import cn.ichensw.neroapicommon.service.InnerInterfaceInfoService;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;

/**
* @author csw
*/
@DubboService
public class InnerInterfaceInfoServiceImpl implements InnerInterfaceInfoService {

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Override
    public InterfaceInfo getInterfaceInfo(String path, String method) {
        return interfaceInfoService.query()
                .eq("url", path)
                .eq("method", method)
                .one();
    }
}




