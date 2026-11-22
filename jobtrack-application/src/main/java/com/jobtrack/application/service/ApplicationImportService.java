package com.jobtrack.application.service;

import com.jobtrack.application.pojo.ImportResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投递台账 Excel 导入。
 */
public interface ApplicationImportService {

    byte[] template();

    ImportResult importFile(MultipartFile file);

    /** 按导入模板的列格式导出当前工作区的全部投递记录。 */
    byte[] exportAll();
}
