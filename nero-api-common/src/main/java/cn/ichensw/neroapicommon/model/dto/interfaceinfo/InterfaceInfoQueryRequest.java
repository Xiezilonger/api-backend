package cn.ichensw.neroapicommon.model.dto.interfaceinfo;

import cn.ichensw.neroapicommon.common.PageRequest;
import cn.ichensw.neroapicommon.model.vo.RequestParamsRemarkVO;
import cn.ichensw.neroapicommon.model.vo.ResponseParamsRemarkVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 查询请求
 *
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class InterfaceInfoQueryRequest extends PageRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 搜索词
     */
    private String searchText;
    /**
     * 主键
     */
    private Long id;
    /**
     * 名称
     */
    private String name;
    /**
     * 描述
     */
    private String description;
    /**
     * 主机名
     */
    private String host;
    /**
     * 接口地址
     */
    private String url;
    /**
     * 请求参数说明
     */
    private List<RequestParamsRemarkVO> requestParamsRemark;
    /**
     * 响应参数说明
     */
    private List<ResponseParamsRemarkVO> responseParamsRemark;
    /**
     * 请求头
     */
    private String requestHeader;
    /**
     * 响应头
     */
    private String responseHeader;
    /**
     * 接口状态（0-关闭，1-开启）
     */
    private Integer status;
    /**
     * 请求类型
     */
    private String method;
    /**
     * 创建人
     */
    private Long userId;
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    /**
     * 是否删除(0-未删, 1-已删)
     */
    private Integer isDelete;
}
