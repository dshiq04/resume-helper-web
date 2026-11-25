package com.jobtrack.application.service;

import com.jobtrack.application.pojo.vo.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 投递台账 Excel 导入与导出。
 */
public interface ApplicationImportService {

    byte[] template();

    ImportResultVO importFile(MultipartFile file);

    /**
     * 导出投递记录：年份 / 投递类型 / 公司名称 / 投递链接 / 投递时间。
     *
     * @param categories 投递类型，为空表示全部类型
     * @param from       投递时间范围起点（含），为空表示不限
     * @param to         投递时间范围终点（含），为空表示不限
     */
    byte[] exportApplications(List<String> categories, LocalDate from, LocalDate to);
}
