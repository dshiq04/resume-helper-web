package com.jobtrack.application.service;

import com.jobtrack.application.pojo.po.Resume;
import com.jobtrack.application.pojo.vo.ResumeListVO;
import com.jobtrack.application.pojo.dto.ResumeUpdateDTO;
import com.jobtrack.application.pojo.vo.ResumeVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历库文件管理。
 */
public interface ResumeService {

    ResumeListVO list(String search);

    ResumeVO get(Long id);

    ResumeVO create(String name, String tagsRaw, MultipartFile file);

    ResumeVO update(Long id, ResumeUpdateDTO request);

    void delete(Long id);

    Resume entity(Long id);

    byte[] readFile(Resume resume);

    String contentType(Resume resume);

    ResumeVO view(Resume resume);
}
