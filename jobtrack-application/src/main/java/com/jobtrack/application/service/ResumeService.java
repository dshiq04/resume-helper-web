package com.jobtrack.application.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jobtrack.application.dto.ResumeListResponse;
import com.jobtrack.application.dto.ResumeUpdateRequest;
import com.jobtrack.application.dto.ResumeView;
import com.jobtrack.application.entity.Resume;
import com.jobtrack.application.mapper.ResumeMapper;
import com.jobtrack.common.constant.DomainConstants;
import com.jobtrack.common.context.TenantContext;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.messaging.AsyncTaskPublisher;
import com.jobtrack.system.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResumeService {

    public static final Map<String, String> SUPPORTED_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("htm", "text/html; charset=utf-8"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("md", "text/markdown; charset=utf-8"),
            Map.entry("markdown", "text/markdown; charset=utf-8"));

    private final ResumeMapper resumeMapper;
    private final FileCrypto fileCrypto;
    private final AppProperties appProperties;
    private final ObjectStorageService objectStorage;
    private final AsyncTaskPublisher publisher;

    public ResumeListResponse list(String search) {
        var query = Wrappers.<Resume>lambdaQuery();
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            query.and(wrapper -> wrapper
                    .apply("LOWER(name) LIKE {0}", like)
                    .or().apply("LOWER(file_name) LIKE {0}", like)
                    .or().apply("LOWER(tags) LIKE {0}", like));
        }
        query.orderByDesc(Resume::getPinned)
                .orderByDesc(Resume::getCreatedAt)
                .orderByDesc(Resume::getId);
        List<Resume> rows = resumeMapper.selectList(query);
        List<ResumeView> items = rows.stream().map(this::view).toList();
        return new ResumeListResponse(items, items.size());
    }

    public ResumeView get(Long id) {
        return view(entity(id));
    }

    @Transactional
    public ResumeView create(String name, String tagsRaw, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("file_required", "请选择要上传的简历文件");
        }
        int maxMb = Math.max(1, appProperties.getStorage().getMaxFileSizeMb());
        if (file.getSize() > (long) maxMb * 1024 * 1024) {
            throw BusinessException.badRequest("file_too_large", "文件不能超过 " + maxMb + " MB");
        }

        String originalName = sanitizeFileName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!SUPPORTED_TYPES.containsKey(extension)) {
            throw BusinessException.badRequest("unsupported_file_type",
                    "仅支持 PDF、DOCX、PPTX、PNG、JPG、WEBP、Markdown 文件");
        }
        if (originalName.isBlank()) {
            originalName = "resume." + extension;
        }

        String resumeName = name == null ? "" : name.trim();
        if (resumeName.isEmpty()) {
            resumeName = originalName.substring(0, originalName.length() - extension.length() - 1);
        }
        if (resumeName.isBlank()) {
            resumeName = "未命名简历";
        }
        if (resumeName.length() > 255) {
            throw BusinessException.badRequest("name_too_long", "简历名称不能超过 255 个字符");
        }
        List<String> tags = parseTags(tagsRaw);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw BusinessException.badRequest("file_unreadable", "无法读取上传的文件");
        }
        if (content.length == 0) {
            throw BusinessException.badRequest("file_empty", "文件内容为空");
        }

        long tenantId = TenantContext.get() == null ? 0L : TenantContext.require();
        String storedName = "resumes/" + tenantId + "/" + generateStoredName();
        objectStorage.put(storedName, fileCrypto.encrypt(content), "application/octet-stream");

        Resume resume = new Resume();
        resume.setName(resumeName);
        resume.setTags(JsonUtils.toJson(tags));
        resume.setFileName(originalName);
        resume.setStoredName(storedName);
        resume.setFileType(extension);
        resume.setFileSize((long) content.length);
        resume.setEncrypted(true);
        resume.setPinned(false);
        try {
            resumeMapper.insert(resume);
        } catch (RuntimeException exception) {
            objectStorage.delete(storedName);
            throw exception;
        }
        return view(resume);
    }

    @Transactional
    public ResumeView update(Long id, ResumeUpdateRequest request) {
        Resume resume = entity(id);
        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw BusinessException.badRequest("name_required", "简历名称不能为空");
            }
            if (name.length() > 255) {
                throw BusinessException.badRequest("name_too_long", "简历名称不能超过 255 个字符");
            }
            resume.setName(name);
        }
        if (request.getTags() != null) {
            resume.setTags(JsonUtils.toJson(normalizeTags(request.getTags())));
        }
        if (request.getPinned() != null) {
            resume.setPinned(request.getPinned());
        }
        resumeMapper.updateById(resume);
        return view(resumeMapper.selectById(id));
    }

    @Transactional
    public void delete(Long id) {
        Resume resume = entity(id);
        resumeMapper.deleteById(id);
        if (!publisher.publishFileCleanup(resume.getStoredName())) {
            objectStorage.delete(resume.getStoredName());
        }
    }

    public Resume entity(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw BusinessException.notFound("not_found", "简历不存在");
        }
        return resume;
    }

    public byte[] readFile(Resume resume) {
        byte[] payload = objectStorage.get(resume.getStoredName());
        if (Boolean.TRUE.equals(resume.getEncrypted())) {
            return fileCrypto.decrypt(payload);
        }
        return payload;
    }

    public String contentType(Resume resume) {
        return SUPPORTED_TYPES.getOrDefault(resume.getFileType(), "application/octet-stream");
    }

    public ResumeView view(Resume resume) {
        ResumeView view = new ResumeView();
        view.setId(resume.getId());
        view.setName(resume.getName());
        view.setTags(parseStoredTags(resume.getTags()));
        view.setFileName(resume.getFileName());
        view.setFileType(resume.getFileType());
        view.setFileSize(resume.getFileSize());
        view.setEncrypted(resume.getEncrypted());
        view.setPinned(resume.getPinned());
        view.setCreatedAt(resume.getCreatedAt());
        view.setUpdatedAt(resume.getUpdatedAt());
        return view;
    }

    private List<String> parseStoredTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return JsonUtils.fromJson(raw, new TypeReference<List<String>>() {
            });
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> values;
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            try {
                values = JsonUtils.fromJson(trimmed, new TypeReference<List<String>>() {
                });
            } catch (IllegalArgumentException exception) {
                throw BusinessException.badRequest("invalid_tags", "简历标签格式不正确");
            }
        } else {
            values = List.of(trimmed.split(","));
        }
        return normalizeTags(values);
    }

    private List<String> normalizeTags(List<String> tags) {
        Set<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String value = tag.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.length() > 32) {
                throw BusinessException.badRequest("tag_too_long", "单个标签不能超过 32 个字符");
            }
            unique.add(value);
            if (unique.size() > 12) {
                throw BusinessException.badRequest("too_many_tags", "最多设置 12 个标签");
            }
        }
        return new ArrayList<>(unique);
    }

    private String generateStoredName() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return stamp + "-" + UUID.randomUUID().toString().replace("-", "") + ".enc";
    }

    private String sanitizeFileName(String value) {
        if (value == null) {
            return "";
        }
        String name = value.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return DomainConstants.normalize(fileName.substring(dot + 1));
    }
}
