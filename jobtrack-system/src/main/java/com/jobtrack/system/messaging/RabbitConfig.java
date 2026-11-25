package com.jobtrack.system.messaging;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.ApplicationRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4.2 的 spring-boot-starter-amqp 只包含 AMQP 1.0 支持，
 * 不会自动创建 RabbitTemplate / @RabbitListener 所需的容器工厂，
 * 因此这里只做最小的连接与模板声明，队列由 @RabbitListener 自动声明。
 */
@Slf4j
@Configuration
@EnableRabbit
public class RabbitConfig {

    @Bean
    public ConnectionFactory connectionFactory(
            @Value("${spring.rabbitmq.host:127.0.0.1}") String host,
            @Value("${spring.rabbitmq.port:5672}") int port,
            @Value("${spring.rabbitmq.username:guest}") String username,
            @Value("${spring.rabbitmq.password:guest}") String password,
            @Value("${spring.rabbitmq.virtual-host:/resume-helper}") String virtualHost) {
        CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        factory.setConnectionTimeout(2000);
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }

    @Bean
    public ApplicationRunner rabbitQueueInitializer(RabbitAdmin rabbitAdmin) {
        return args -> {
            try {
                rabbitAdmin.declareQueue(new Queue(MessagingQueues.FILE_CLEANUP, true));
                rabbitAdmin.declareQueue(new Queue(MessagingQueues.TOKEN_LOG, true));
                rabbitAdmin.declareQueue(new Queue(MessagingQueues.TOKEN_INVALIDATE, true));
                rabbitAdmin.declareQueue(new Queue(MessagingQueues.USER_BAN, true));
                rabbitAdmin.declareQueue(new Queue(MessagingQueues.USER_OPERATION, true));
                log.info("RabbitMQ 队列已声明");
            } catch (Exception exception) {
                log.warn("RabbitMQ 队列声明失败（Broker 不可用时会自动降级）: {}", exception.getMessage());
            }
        };
    }

    @Bean
    public Queue fileCleanupQueue() {
        return new Queue(MessagingQueues.FILE_CLEANUP, true);
    }

    @Bean
    public Queue tokenLogQueue() {
        return new Queue(MessagingQueues.TOKEN_LOG, true);
    }

    @Bean
    public Queue tokenInvalidateQueue() {
        return new Queue(MessagingQueues.TOKEN_INVALIDATE, true);
    }

    @Bean
    public Queue userBanQueue() {
        return new Queue(MessagingQueues.USER_BAN, true);
    }

    @Bean
    public Queue userOperationQueue() {
        return new Queue(MessagingQueues.USER_OPERATION, true);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMissingQueuesFatal(false);
        // 消费异常时拒绝并重新入队，确保消息不会被静默丢弃。
        factory.setDefaultRequeueRejected(true);
        return factory;
    }

    /**
     * 令牌注销使用手动确认：消费者只有在 Redis 删除成功后才 ack，
     * 失败时 nack 重新入队，避免“消息已确认但令牌仍然有效”的窗口。
     */
    @Bean("manualAckListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory manualAckListenerContainerFactory(
            @Qualifier("connectionFactory") ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMissingQueuesFatal(false);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
