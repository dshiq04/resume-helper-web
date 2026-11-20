package com.jobtrack.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.application.entity.ApplicationRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApplicationRecordMapper extends BaseMapper<ApplicationRecord> {
}
