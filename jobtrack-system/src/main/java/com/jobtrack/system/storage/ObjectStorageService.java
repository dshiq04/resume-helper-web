package com.jobtrack.system.storage;

import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 统一的对象存储：使用 MinIO SDK 接入 S3 兼容的 OSS（MinIO / 阿里云 OSS / AWS S3），
 * 未配置密钥或连接失败时自动退回本地磁盘（data 目录）。
 *
 * <p>数据库只保存对象 key（路径），对外展示时通过 publicBaseUrl 拼成完整地址。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectStorageService {

    private final AppProperties appProperties;

    private volatile MinioClient client;

    @PostConstruct
    void init() {
        AppProperties.Storage storage = appProperties.getStorage();
        if (!"s3".equalsIgnoreCase(storage.getType())) {
            log.info("文件存储: 本地磁盘");
            return;
        }
        if (isBlank(storage.getAccessKey()) || isBlank(storage.getSecretKey())) {
            log.warn("S3 未配置 access-key / secret-key，简历与头像将写入本地磁盘");
            return;
        }
        try {
            client = MinioClient.builder()
                    .endpoint(storage.getEndpoint())
                    .credentials(storage.getAccessKey(), storage.getSecretKey())
                    .build();
            ensureBucket(storage);
            applyPublicReadPolicy(storage);
            log.info("文件存储: S3 {} / {}", storage.getEndpoint(), storage.getBucket());
        } catch (Exception exception) {
            client = null;
            log.error("S3 初始化失败，改用本地磁盘: {}", exception.getMessage());
        }
    }

    public boolean s3Enabled() {
        return client != null;
    }

    public void put(String key, byte[] data, String contentType) {
        if (client != null) {
            try {
                client.putObject(PutObjectArgs.builder()
                        .bucket(bucket())
                        .object(key)
                        .stream(new ByteArrayInputStream(data), data.length, -1)
                        .contentType(contentType == null ? "application/octet-stream" : contentType)
                        .build());
            } catch (Exception exception) {
                throw BusinessException.internal("storage_unavailable", "无法保存文件");
            }
            return;
        }
        Path target = localPath(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException exception) {
            throw BusinessException.internal("storage_unavailable", "无法保存文件");
        }
    }

    public byte[] get(String key) {
        if (key == null || key.isBlank()) {
            throw BusinessException.notFound("file_missing", "文件已丢失");
        }
        if (client != null) {
            try (GetObjectResponse response = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket()).object(key).build())) {
                return response.readAllBytes();
            } catch (ErrorResponseException exception) {
                if (exception.response() != null && exception.response().code() == 404) {
                    // 兼容早期写入本地磁盘、后来才启用 S3 的文件。
                    Path local = localPath(key);
                    if (Files.isRegularFile(local)) {
                        try {
                            return Files.readAllBytes(local);
                        } catch (IOException ignored) {
                            // 继续按缺失处理。
                        }
                    }
                    throw BusinessException.notFound("file_missing", "文件已丢失");
                }
                throw BusinessException.internal("storage_unavailable", "无法读取文件");
            } catch (Exception exception) {
                throw BusinessException.internal("file_unreadable", "无法读取文件");
            }
        }
        Path path = localPath(key);
        if (!Files.isRegularFile(path)) {
            throw BusinessException.notFound("file_missing", "文件已丢失");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw BusinessException.internal("file_unreadable", "无法读取文件");
        }
    }

    public boolean exists(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (client == null) {
            return Files.isRegularFile(localPath(key));
        }
        try {
            client.statObject(io.minio.StatObjectArgs.builder()
                    .bucket(bucket()).object(key).build());
            return true;
        } catch (ErrorResponseException exception) {
            return Files.isRegularFile(localPath(key));
        } catch (Exception exception) {
            return false;
        }
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (client != null) {
            try {
                client.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket()).object(key).build());
            } catch (Exception exception) {
                log.warn("删除对象失败 {}: {}", key, exception.getMessage());
            }
            return;
        }
        try {
            Files.deleteIfExists(localPath(key));
        } catch (IOException ignored) {
            // 清理失败不影响数据库操作。
        }
    }

    /**
     * 数据库中只存 key，这里拼成对外可访问的完整地址。
     */
    public String publicUrl(String key) {
        if (client == null || key == null || key.isBlank()) {
            return null;
        }
        AppProperties.Storage storage = appProperties.getStorage();
        String base = storage.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            return null;
        }
        return base.replaceAll("/+$", "") + "/" + key.replaceAll("^/+", "");
    }

    private void ensureBucket(AppProperties.Storage storage) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder()
                .bucket(storage.getBucket()).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(storage.getBucket()).build());
            log.info("已创建存储桶: {}", storage.getBucket());
        }
    }

    private void applyPublicReadPolicy(AppProperties.Storage storage) {
        if (!storage.isPublicRead() || client == null) {
            return;
        }
        String bucket = storage.getBucket();
        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{"
                + "\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                + "\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
        try {
            client.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket).config(policy).build());
        } catch (Exception exception) {
            log.warn("设置存储桶公共读策略失败: {}", exception.getMessage());
        }
    }

    private String bucket() {
        return appProperties.getStorage().getBucket();
    }

    private Path localPath(String key) {
        return appProperties.dataPath().resolve(key);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
