package com.jobtrack.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.system.entity.AccessTokenLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccessTokenLogMapper extends BaseMapper<AccessTokenLog> {
}
