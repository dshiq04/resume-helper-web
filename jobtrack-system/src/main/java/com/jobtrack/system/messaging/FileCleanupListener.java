package com.jobtrack.system.messaging;

import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 异步删除对象存储中的文件，队列在首次连接时自动声明。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupListener {

    private final ObjectStorageService objectStorage;

    @RabbitListener(queuesToDeclare = @Queue(name = MessagingQueues.FILE_CLEANUP, durable = "true"))
    public void handle(String payload) {
        try {
            FileCleanupMessage message = JsonUtils.fromJson(payload, FileCleanupMessage.class);
            if (message != null && message.key() != null) {
                objectStorage.delete(message.key());
            }
        } catch (Exception exception) {
            log.warn("异步删除文件失败: {}", exception.getMessage());
        }
    }
}
