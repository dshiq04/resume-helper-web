package com.jobtrack.system.storage;

import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 统一的对象存储：支持 S3 兼容的 OSS（MinIO / 阿里云 OSS / AWS S3），
 * 未配置密钥时自动退回本地磁盘（data 目录）。
 *
 * <p>数据库只保存对象 key（路径），对外展示时通过 publicBaseUrl 拼成完整地址。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectStorageService {

    private final AppProperties appProperties;

    private volatile S3Client client;

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
            client = S3Client.builder()
                    .endpointOverride(URI.create(storage.getEndpoint()))
                    .region(Region.of(storage.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(storage.getAccessKey(), storage.getSecretKey())))
                    .forcePathStyle(storage.isPathStyle())
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
            client.putObject(
                    builder -> builder.bucket(bucket()).key(key)
                            .contentType(contentType == null ? "application/octet-stream" : contentType),
                    RequestBody.fromBytes(data));
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
            try {
                return client.getObjectAsBytes(builder -> builder.bucket(bucket()).key(key)).asByteArray();
            } catch (NoSuchKeyException exception) {
                throw BusinessException.notFound("file_missing", "文件已丢失");
            } catch (S3Exception exception) {
                throw BusinessException.internal("storage_unavailable", "无法读取文件");
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

    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (client != null) {
            try {
                client.deleteObject(builder -> builder.bucket(bucket()).key(key));
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

    private void ensureBucket(AppProperties.Storage storage) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(storage.getBucket()).build());
        } catch (NoSuchBucketException exception) {
            client.createBucket(CreateBucketRequest.builder().bucket(storage.getBucket()).build());
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
            client.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .bucket(bucket).policy(policy).build());
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
