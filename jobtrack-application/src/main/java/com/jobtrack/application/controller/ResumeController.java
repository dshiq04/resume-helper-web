package com.jobtrack.application.controller;

import com.jobtrack.common.api.Result;

import com.jobtrack.application.pojo.vo.ResumeListVO;
import com.jobtrack.application.pojo.dto.ResumeUpdateDTO;
import com.jobtrack.application.pojo.vo.ResumeVO;
import com.jobtrack.application.pojo.po.Resume;
import com.jobtrack.application.service.ResumeService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @GetMapping("/resumes")
    public Result<ResumeListVO> list(@RequestParam(required = false) String search) {
        return Result.success(resumeService.list(search));
    }

    @PostMapping("/resumes")
    public Result<ResumeVO> upload(@RequestParam(required = false) String name,
                             @RequestParam(required = false) String tags,
                             @RequestParam("file") MultipartFile file) {
        return Result.success(resumeService.create(name, tags, file));
    }

    @GetMapping("/resume/{id}")
    public Result<ResumeVO> get(@PathVariable Long id) {
        return Result.success(resumeService.get(id));
    }

    @PutMapping("/resume/{id}")
    public Result<ResumeVO> update(@PathVariable Long id, @RequestBody ResumeUpdateDTO request) {
        return Result.success(resumeService.update(id, request));
    }

    @DeleteMapping("/resume/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return Result.success(Map.of("deleted", 1));
    }

    @GetMapping("/resume/{id}/file")
    public void download(@PathVariable Long id,
                         @RequestParam(defaultValue = "0") String download,
                         HttpServletResponse response) throws IOException {
        Resume resume = resumeService.entity(id);
        byte[] content = resumeService.readFile(resume);
        String mode = "1".equals(download) ? "attachment" : "inline";

        response.setContentType(resumeService.contentType(resume));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                contentDisposition(mode, resume.getFileName()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }

    private String contentDisposition(String mode, String fileName) {
        String ascii = fileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "_");
        if (ascii.isBlank()) {
            ascii = "resume";
        }
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return mode + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
