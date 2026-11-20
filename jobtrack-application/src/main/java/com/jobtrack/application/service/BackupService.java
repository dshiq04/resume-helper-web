package com.jobtrack.application.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jobtrack.application.dto.BackupImportResult;
import com.jobtrack.application.dto.BackupManifest;
import com.jobtrack.application.entity.ApplicationChange;
import com.jobtrack.application.entity.ApplicationRecord;
import com.jobtrack.application.entity.DeletedApplication;
import com.jobtrack.application.entity.Resume;
import com.jobtrack.application.mapper.ApplicationChangeMapper;
import com.jobtrack.application.mapper.ApplicationRecordMapper;
import com.jobtrack.application.mapper.DeletedApplicationMapper;
import com.jobtrack.application.mapper.ResumeMapper;
import com.jobtrack.common.context.TenantContext;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.entity.SysTenant;
import com.jobtrack.system.entity.TenantSetting;
import com.jobtrack.system.mapper.SysTenantMapper;
import com.jobtrack.system.mapper.TenantSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 单账号数据导入导出。导出包内含 manifest.json、业务数据与解密后的简历原文件；
 * 导入时重新生成主键并按当前账号重新加密简历，因此可以安全迁移到另一个账号。
 */
@Service
@RequiredArgsConstructor
public class BackupService {

    private final ApplicationRecordMapper applicationMapper;
    private final ApplicationChangeMapper changeMapper;
    private final ResumeMapper resumeMapper;
    private final DeletedApplicationMapper deletedMapper;
    private final TenantSettingMapper settingMapper;
    private final SysTenantMapper tenantMapper;
    private final ResumeService resumeService;
    private final FileCrypto fileCrypto;
    private final AppProperties appProperties;
    private final ApplicationCacheService applicationCache;

    public byte[] exportData() {
        long tenantId = TenantContext.require();
        SysTenant tenant = tenantMapper.selectById(tenantId);

        List<ApplicationRecord> applications = applicationMapper.selectList(
                Wrappers.<ApplicationRecord>lambdaQuery().orderByAsc(ApplicationRecord::getId));
        List<ApplicationChange> changes = changeMapper.selectList(
                Wrappers.<ApplicationChange>lambdaQuery().orderByAsc(ApplicationChange::getId));
        List<Resume> resumes = resumeMapper.selectList(
                Wrappers.<Resume>lambdaQuery().orderByAsc(Resume::getId));
        List<TenantSetting> settings = settingMapper.selectList(
                Wrappers.<TenantSetting>lambdaQuery().orderByAsc(TenantSetting::getId));
        List<DeletedApplication> trash = deletedMapper.selectList(
                Wrappers.<DeletedApplication>lambdaQuery().orderByAsc(DeletedApplication::getId));

        List<String> warnings = new ArrayList<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("applications", (long) applications.size());
        counts.put("changes", (long) changes.size());
        counts.put("resumes", (long) resumes.size());
        counts.put("settings", (long) settings.size());
        counts.put("trash", (long) trash.size());

        BackupManifest manifest = new BackupManifest();
        manifest.setExportedAt(LocalDateTime.now());
        manifest.setAppVersion(appProperties.getVersion());
        manifest.setTenantName(tenant == null ? "" : tenant.getName());
        manifest.setCounts(counts);
        manifest.setWarnings(warnings);

        // 先读取文件，统计信息完整后再写 manifest，避免 ZIP 内出现同名重复条目。
        Map<String, byte[]> resumeFiles = new LinkedHashMap<>();
        long fileBytes = 0;
        for (Resume resume : resumes) {
            Path path = resumeService.tenantDirectory().resolve(resume.getStoredName());
            if (!Files.isRegularFile(path)) {
                warnings.add("简历文件缺失：" + resume.getFileName());
                continue;
            }
            try {
                byte[] plain = resumeService.readFile(resume);
                resumeFiles.put(resume.getStoredName(), plain);
                fileBytes += plain.length;
            } catch (BusinessException exception) {
                warnings.add("简历文件读取失败：" + resume.getFileName());
            }
        }
        counts.put("files", (long) resumeFiles.size());
        counts.put("bytes", fileBytes);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            writeJson(zip, "manifest.json", manifest);
            writeJson(zip, "applications.json", applications);
            writeJson(zip, "changes.json", changes);
            writeJson(zip, "resumes.json", resumes);
            writeJson(zip, "settings.json", settings);
            writeJson(zip, "trash.json", trash);

            for (Map.Entry<String, byte[]> fileEntry : resumeFiles.entrySet()) {
                ZipEntry entry = new ZipEntry("files/" + fileEntry.getKey());
                zip.putNextEntry(entry);
                zip.write(fileEntry.getValue());
                zip.closeEntry();
            }

            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw BusinessException.internal("export_failed", "导出备份失败");
        }
    }

    @Transactional
    public BackupImportResult importData(MultipartFile file, boolean includeTrash) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("file_required", "请选择备份文件");
        }
        Map<String, byte[]> entries = readZip(file);
        byte[] manifestBytes = entries.get("manifest.json");
        if (manifestBytes == null) {
            throw BusinessException.badRequest("invalid_backup", "备份文件缺少 manifest.json");
        }
        BackupManifest manifest = JsonUtils.fromJson(new String(manifestBytes), BackupManifest.class);
        if (manifest.getVersion() > 1) {
            throw BusinessException.badRequest("unsupported_version", "备份版本高于当前服务，无法导入");
        }

        List<ApplicationRecord> applications = readList(entries.get("applications.json"),
                new TypeReference<List<ApplicationRecord>>() {
                });
        List<ApplicationChange> changes = readList(entries.get("changes.json"),
                new TypeReference<List<ApplicationChange>>() {
                });
        List<Resume> resumes = readList(entries.get("resumes.json"),
                new TypeReference<List<Resume>>() {
                });
        List<TenantSetting> settings = readList(entries.get("settings.json"),
                new TypeReference<List<TenantSetting>>() {
                });

        List<String> warnings = new ArrayList<>();

        Map<Long, Long> applicationIds = new HashMap<>();
        for (ApplicationRecord application : applications) {
            Long oldId = application.getId();
            application.setId(null);
            application.setTenantId(null);
            application.setStatusDays(null);
            application.setScreeningDays(null);
            if (application.getApplyYear() == null) {
                application.setApplyYear(application.getAppliedAt() == null
                        ? LocalDateTime.now().getYear() : application.getAppliedAt().getYear());
            }
            applicationMapper.insert(application);
            if (oldId != null) {
                applicationIds.put(oldId, application.getId());
            }
        }

        int importedChanges = 0;
        for (ApplicationChange change : changes) {
            Long mapped = applicationIds.get(change.getApplicationId());
            if (mapped == null) {
                continue;
            }
            change.setId(null);
            change.setTenantId(null);
            change.setApplicationId(mapped);
            change.setCreatedAt(null);
            changeMapper.insert(change);
            importedChanges++;
        }

        int importedResumes = 0;
        Path directory = resumeService.tenantDirectory();
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw BusinessException.internal("storage_unavailable", "无法创建简历目录");
        }
        for (Resume resume : resumes) {
            byte[] content = entries.get("files/" + resume.getStoredName());
            if (content == null) {
                warnings.add("备份中缺少简历文件：" + resume.getFileName());
                continue;
            }
            String storedName = "backup-" + UUID.randomUUID().toString().replace("-", "") + ".enc";
            try {
                Files.write(directory.resolve(storedName), fileCrypto.encrypt(content));
            } catch (IOException exception) {
                warnings.add("简历文件写入失败：" + resume.getFileName());
                continue;
            }
            resume.setId(null);
            resume.setTenantId(null);
            resume.setStoredName(storedName);
            resume.setFileSize((long) content.length);
            resume.setEncrypted(true);
            resume.setCreatedAt(null);
            resume.setUpdatedAt(null);
            resumeMapper.insert(resume);
            importedResumes++;
        }

        int importedSettings = 0;
        for (TenantSetting setting : settings) {
            if (setting.getSettingKey() == null || setting.getSettingValue() == null) {
                continue;
            }
            TenantSetting existing = settingMapper.selectOne(Wrappers.<TenantSetting>lambdaQuery()
                    .eq(TenantSetting::getSettingKey, setting.getSettingKey()));
            if (existing == null) {
                setting.setId(null);
                setting.setTenantId(null);
                setting.setUpdatedAt(null);
                settingMapper.insert(setting);
            } else {
                existing.setSettingValue(setting.getSettingValue());
                settingMapper.updateById(existing);
            }
            importedSettings++;
        }

        int importedTrash = 0;
        if (includeTrash) {
            List<DeletedApplication> trash = readList(entries.get("trash.json"),
                    new TypeReference<List<DeletedApplication>>() {
                    });
            for (DeletedApplication item : trash) {
                item.setId(null);
                item.setTenantId(null);
                item.setCreatedAt(null);
                deletedMapper.insert(item);
                importedTrash++;
            }
        }

        BackupImportResult result = new BackupImportResult();
        result.setApplications(applications.size());
        result.setChanges(importedChanges);
        result.setResumes(importedResumes);
        result.setSettings(importedSettings);
        result.setWarnings(warnings);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("applications", (long) applications.size());
        counts.put("changes", (long) importedChanges);
        counts.put("resumes", (long) importedResumes);
        counts.put("settings", (long) importedSettings);
        counts.put("trash", (long) importedTrash);
        result.setCounts(counts);
        applicationCache.invalidate();
        return result;
    }

    private void writeJson(ZipOutputStream zip, String name, Object value) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(JsonUtils.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        zip.closeEntry();
    }

    private Map<String, byte[]> readZip(MultipartFile file) {
        Map<String, byte[]> entries = new HashMap<>();
        try (InputStream input = file.getInputStream(); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.contains("..")) {
                    throw BusinessException.badRequest("invalid_backup", "备份文件包含非法路径");
                }
                entries.put(name, zip.readAllBytes());
            }
            return entries;
        } catch (IOException exception) {
            throw BusinessException.badRequest("invalid_backup", "无法读取备份文件");
        }
    }

    private <T> List<T> readList(byte[] content, TypeReference<List<T>> type) {
        if (content == null) {
            return List.of();
        }
        try {
            return JsonUtils.fromJson(new String(content), type);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("invalid_backup", "备份数据格式不正确");
        }
    }
}
