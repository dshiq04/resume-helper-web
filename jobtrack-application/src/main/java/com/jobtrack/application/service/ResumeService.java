package com.jobtrack.application.service;

import com.jobtrack.application.pojo.Resume;
import com.jobtrack.application.pojo.ResumeListResponse;
import com.jobtrack.application.pojo.ResumeUpdateRequest;
import com.jobtrack.application.pojo.ResumeView;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历库文件管理。
 */
public interface ResumeService {

    ResumeListResponse list(String search);

    ResumeView get(Long id);

    ResumeView create(String name, String tagsRaw, MultipartFile file);

    ResumeView update(Long id, ResumeUpdateRequest request);

    void delete(Long id);

    Resume entity(Long id);

    byte[] readFile(Resume resume);

    String contentType(Resume resume);

    ResumeView view(Resume resume);
}
