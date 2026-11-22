package com.jobtrack.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.system.pojo.po.TenantSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantSettingMapper extends BaseMapper<TenantSetting> {
}
