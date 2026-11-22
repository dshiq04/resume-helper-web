package com.jobtrack.system.messaging;

/** 队列名称常量，发布端直接发到默认交换机。 */
public final class MessagingQueues {

    public static final String FILE_CLEANUP = "jobtrack.file.cleanup";
    public static final String TOKEN_LOG = "jobtrack.token.log";
    public static final String TOKEN_INVALIDATE = "jobtrack.token.invalidate";

    private MessagingQueues() {
    }
}
