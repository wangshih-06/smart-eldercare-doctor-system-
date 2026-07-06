package com.medical.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.common.push.WarningSseManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 预警实时推送 Redis 发布/订阅配置
 * 仅作为分布式部署的补充推送通道，单机模式由 WarningServiceImpl 直接推送 SSE
 */
@Configuration
@ConditionalOnProperty(name = "medical.warning.redis-pubsub.enabled", havingValue = "true", matchIfMissing = false)
public class RedisMessageConfig {

    public static final String CHANNEL_WARNING_CREATED = "warning:created";
    public static final String CHANNEL_WARNING_UPDATED = "warning:updated";

    @Autowired
    private WarningSseManager warningSseManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public WarningMessageSubscriber warningMessageSubscriber() {
        return new WarningMessageSubscriber();
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListenerAdapter adapter = new MessageListenerAdapter(warningMessageSubscriber(), "onMessage");
        adapter.setSerializer(new StringRedisSerializer());
        container.addMessageListener(adapter, new ChannelTopic(CHANNEL_WARNING_CREATED));
        container.addMessageListener(adapter, new ChannelTopic(CHANNEL_WARNING_UPDATED));
        return container;
    }

    /**
     * 消息体格式: {"targetUserId": 1, "eventName": "warning-created", "data": {...}}
     */
    public class WarningMessageSubscriber {
        public void onMessage(String message) {
            try {
                JsonNode node = objectMapper.readTree(message);
                Long targetUserId = node.hasNonNull("targetUserId") ? node.get("targetUserId").asLong() : null;
                String eventName = node.hasNonNull("eventName") ? node.get("eventName").asText() : "warning-created";
                JsonNode data = node.get("data");
                warningSseManager.pushToUser(targetUserId, eventName, data);
            } catch (Exception ignored) {
                // 消息格式异常，忽略
            }
        }
    }
}
