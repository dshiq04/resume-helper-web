package com.jobtrack.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.system.pojo.po.UserOperationLog;
import com.jobtrack.system.pojo.vo.UserActivityVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户操作记录。所有查询都需要跨租户，因此整张表绕过租户拦截器；
 * tenant_id 仍随记录一起写入，便于后续审计。
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface UserOperationLogMapper extends BaseMapper<UserOperationLog> {

    /** 批量查询用户最后一次操作 / 登录时间。 */
    @Select("<script>"
            + "SELECT user_id, MAX(created_at) AS last_operation_at, "
            + "MAX(CASE WHEN operation_type = 'LOGIN' THEN created_at END) AS last_login_at "
            + "FROM user_operation_log WHERE user_id IN "
            + "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY user_id"
            + "</script>")
    List<UserActivityVO> selectActivity(@Param("userIds") List<Long> userIds);
}
