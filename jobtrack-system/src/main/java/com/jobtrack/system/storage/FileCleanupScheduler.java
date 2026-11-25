package com.jobtrack.system.storage;

import com.jobtrack.system.messaging.AsyncTaskPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 文件清理与数据库事务对齐。
 *
 * <p>删除文件必须等到事务提交后再执行：如果事务回滚，数据库仍然引用该文件，
 * 提前删除会造成“记录存在但文件丢失”。上传新文件时则在事务回滚后删除孤儿文件。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCleanupScheduler {

    private final AsyncTaskPublisher publisher;
    private final ObjectStorageService objectStorage;

    /** 事务提交后删除文件；无事务时立即删除。 */
    public void deleteAfterCommit(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        afterCommit(() -> deleteNow(key));
    }

    /** 事务回滚时删除刚上传、尚未被数据库引用的文件。 */
    public void deleteOnRollback(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteNow(key);
                }
            }
        });
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    private void deleteNow(String key) {
        try {
            // Broker 可用时交给异步队列；不可用时同步删除。
            if (!publisher.publishFileCleanup(key)) {
                objectStorage.delete(key);
            }
        } catch (Exception exception) {
            log.warn("清理文件失败 {}: {}", key, exception.getMessage());
        }
    }
}
