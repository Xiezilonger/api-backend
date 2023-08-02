package cn.ichensw.neroapiadmin.mapper;

import cn.ichensw.neroapicommon.model.entity.UserInterfaceInfo;
import cn.ichensw.neroapicommon.model.vo.InterfaceInfoVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
* @author csw
* @description 针对表【user_interface_info(用户调用接口关系)】的数据库操作Mapper
* @createDate 2023-06-17 12:32:57
* @Entity cn.ichensw.neroapiadmin.model.entity.UserInterfaceInfo
*/
public interface UserInterfaceInfoMapper extends BaseMapper<UserInterfaceInfo> {

    /**
     * 获取接口调用排名前 n 的接口信息
     *
     * @param limit 前几名
     * @return List<InterfaceInfoVO>
     */
    List<UserInterfaceInfo> listTopInvokeInterfaceInfo(int limit);
}




