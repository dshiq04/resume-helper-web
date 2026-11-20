package com.jobtrack.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.application.entity.DeletedApplication;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeletedApplicationMapper extends BaseMapper<DeletedApplication> {
}
