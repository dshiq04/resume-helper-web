package com.jobtrack.application.controller;

import com.jobtrack.application.pojo.vo.ResumeListVO;
import com.jobtrack.application.pojo.dto.ResumeUpdateDTO;
import com.jobtrack.application.pojo.vo.ResumeVO;
import com.jobtrack.application.pojo.po.Resume;
import com.jobtrack.application.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @GetMapping("/resumes")
    public ResumeListVO list(@RequestParam(required = false) String search) {
        return resumeService.list(search);
    }

    @PostMapping("/resumes")
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeVO upload(@RequestParam(required = false) String name,
                             @RequestParam(required = false) String tags,
                             @RequestParam("file") MultipartFile file) {
        return resumeService.create(name, tags, file);
    }

    @GetMapping("/resume/{id}")
    public ResumeVO get(@PathVariable Long id) {
        return resumeService.get(id);
    }

    @PutMapping("/resume/{id}")
    public ResumeVO update(@PathVariable Long id, @RequestBody ResumeUpdateDTO request) {
        return resumeService.update(id, request);
    }

    @DeleteMapping("/resume/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return Map.of("deleted", 1);
    }

    @GetMapping("/resume/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable Long id,
                                           @RequestParam(defaultValue = "0") String download) {
        Resume resume = resumeService.entity(id);
        byte[] content = resumeService.readFile(resume);
        String mode = "1".equals(download) ? "attachment" : "inline";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(resumeService.contentType(resume)));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(mode, resume.getFileName()));
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
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
