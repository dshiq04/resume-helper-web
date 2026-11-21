package com.jobtrack.system.messaging;

/** 异步清理文件（简历、旧头像）。 */
public record FileCleanupMessage(String key) {
}
