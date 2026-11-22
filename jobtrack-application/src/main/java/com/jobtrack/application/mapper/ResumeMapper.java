package com.jobtrack.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobtrack.application.pojo.po.Resume;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {
}
