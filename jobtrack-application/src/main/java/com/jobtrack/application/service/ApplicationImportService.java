package com.jobtrack.application.service;

import com.jobtrack.application.pojo.ImportResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投递台账 Excel 导入。
 */
public interface ApplicationImportService {

    byte[] template();

    ImportResult importFile(MultipartFile file);
}
