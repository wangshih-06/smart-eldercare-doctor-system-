package com.medical.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.common.config.RedisMessageConfig;
import com.medical.common.exception.BusinessException;
import com.medical.common.push.WarningSseManager;
import com.medical.entity.ElderInfo;
import com.medical.entity.HealthWarning;
import com.medical.entity.TimelineEvent;
import com.medical.entity.WarningEventLog;
import com.medical.mapper.ElderInfoMapper;
import com.medical.mapper.HealthWarningMapper;
import com.medical.mapper.WarningEventLogMapper;
import com.medical.service.TimelineService;
import com.medical.service.WarningService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class WarningServiceImpl implements WarningService {

    @Autowired
    private HealthWarningMapper healthWarningMapper;

    @Autowired
    private WarningEventLogMapper warningEventLogMapper;

    @Autowired
    private ElderInfoMapper elderInfoMapper;

    @Autowired
    private TimelineService timelineService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarningSseManager warningSseManager;

    @Override
    public Page<HealthWarning> list(Integer pageNum, Integer pageSize, Integer status, Integer warningLevel, Long elderId) {
        Page<HealthWarning> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<HealthWarning> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, HealthWarning::getStatus, status)
               .eq(warningLevel != null, HealthWarning::getWarningLevel, warningLevel)
               .eq(elderId != null, HealthWarning::getElderId, elderId)
               .orderByDesc(HealthWarning::getWarningLevel)
               .orderByDesc(HealthWarning::getCreateTime);
        return healthWarningMapper.selectPage(page, wrapper);
    }

    @Override
    public HealthWarning getDetail(Long id) {
        HealthWarning warning = healthWarningMapper.selectById(id);
        if (warning == null) {
            throw new BusinessException(404, "预警不存在");
        }
        return warning;
    }

    @Override
    public void handle(Long id, String handleResult, Long doctorId) {
        HealthWarning entity = healthWarningMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "预警不存在");
        }
        entity.setStatus(2);
        entity.setHandleTime(LocalDateTime.now());
        entity.setHandleResult(handleResult);
        entity.setDoctorId(doctorId);
        healthWarningMapper.updateById(entity);

        writeEventLog(id, 4, doctorId, "处理: " + handleResult);
        writeTimelineForHandle(entity, "预警已处理: " + entity.getWarningTitle(), handleResult);
        publish(RedisMessageConfig.CHANNEL_WARNING_UPDATED, doctorId, "warning-updated", entity);
    }

    @Override
    public void ignore(Long id, String handleResult) {
        HealthWarning entity = healthWarningMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "预警不存在");
        }
        entity.setStatus(3);
        entity.setHandleTime(LocalDateTime.now());
        entity.setHandleResult(handleResult);
        healthWarningMapper.updateById(entity);

        writeEventLog(id, 5, entity.getDoctorId(), "忽略: " + handleResult);
        writeTimelineForHandle(entity, "预警已忽略: " + entity.getWarningTitle(), handleResult);
        publish(RedisMessageConfig.CHANNEL_WARNING_UPDATED, entity.getDoctorId(), "warning-updated", entity);
    }

    @Override
    public Long create(HealthWarning warning) {
        return createAndPush(warning);
    }

    @Override
    public Long createAndPush(HealthWarning warning) {
        warning.setStatus(0);
        if (warning.getPushStatus() == null) warning.setPushStatus(0);
        if (warning.getReadStatus() == null) warning.setReadStatus(0);
        if (warning.getHandleDeadline() == null) {
            warning.setHandleDeadline(calcDeadline(warning.getWarningLevel()));
        }
        healthWarningMapper.insert(warning);

        writeEventLog(warning.getId(), 1, null, "预警创建: " + warning.getWarningTitle());

        TimelineEvent event = new TimelineEvent();
        event.setElderId(warning.getElderId());
        event.setEventType(4);
        event.setEventTitle("智能预警: " + warning.getWarningTitle());
        event.setEventContent(warning.getWarningContent());
        event.setSourceType("warning");
        event.setSourceId(warning.getId());
        event.setEventTime(LocalDateTime.now());
        timelineService.addEvent(event);

        Long targetDoctorId = resolveTargetDoctor(warning);
        if (targetDoctorId != null) {
            warning.setPushStatus(1);
            healthWarningMapper.updateById(warning);
            writeEventLog(warning.getId(), 2, null, "推送给医生: " + targetDoctorId);
            publish(RedisMessageConfig.CHANNEL_WARNING_CREATED, targetDoctorId, "warning-created", warning);
        }
        return warning.getId();
    }

    @Override
    public void markRead(Long id, Long operatorId) {
        HealthWarning entity = healthWarningMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "预警不存在");
        }
        entity.setReadStatus(1);
        healthWarningMapper.updateById(entity);
        writeEventLog(id, 3, operatorId, "标记已读");
    }

    @Override
    public Map<String, Object> getRealtimeStats(Long doctorId) {
        Map<String, Object> stats = new HashMap<>();
        LambdaQueryWrapper<HealthWarning> baseWrapper = new LambdaQueryWrapper<HealthWarning>()
                .eq(doctorId != null, HealthWarning::getDoctorId, doctorId);

        stats.put("unread", healthWarningMapper.selectCount(
                new LambdaQueryWrapper<HealthWarning>()
                        .eq(doctorId != null, HealthWarning::getDoctorId, doctorId)
                        .eq(HealthWarning::getReadStatus, 0)));
        stats.put("high", healthWarningMapper.selectCount(
                new LambdaQueryWrapper<HealthWarning>()
                        .eq(doctorId != null, HealthWarning::getDoctorId, doctorId)
                        .eq(HealthWarning::getWarningLevel, 3)
                        .eq(HealthWarning::getStatus, 0)));
        stats.put("todayNew", healthWarningMapper.selectCount(
                new LambdaQueryWrapper<HealthWarning>()
                        .eq(doctorId != null, HealthWarning::getDoctorId, doctorId)
                        .ge(HealthWarning::getCreateTime, LocalDate.now().atStartOfDay())));
        return stats;
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pending", healthWarningMapper.selectCount(
                new LambdaQueryWrapper<HealthWarning>().eq(HealthWarning::getStatus, 0)));
        stats.put("handled", healthWarningMapper.selectCount(
                new LambdaQueryWrapper<HealthWarning>().eq(HealthWarning::getStatus, 2)));
        stats.put("red", healthWarningMapper.selectCount(
                new LambdaQueryWrapper<HealthWarning>().eq(HealthWarning::getWarningLevel, 3).eq(HealthWarning::getStatus, 0)));
        stats.put("orange", healthWarningMapper.selectCount(
                new LambdaQueryWrapper<HealthWarning>().eq(HealthWarning::getWarningLevel, 2).eq(HealthWarning::getStatus, 0)));
        stats.put("yellow", healthWarningMapper.selectCount(
                new LambdaQueryWrapper<HealthWarning>().eq(HealthWarning::getWarningLevel, 1).eq(HealthWarning::getStatus, 0)));
        return stats;
    }

    private Long resolveTargetDoctor(HealthWarning warning) {
        if (warning.getDoctorId() != null) {
            return warning.getDoctorId();
        }
        if (warning.getElderId() == null) return null;
        ElderInfo elder = elderInfoMapper.selectById(warning.getElderId());
        return elder != null ? elder.getDoctorId() : null;
    }

    private LocalDateTime calcDeadline(Integer warningLevel) {
        int level = warningLevel == null ? 1 : warningLevel;
        LocalDateTime now = LocalDateTime.now();
        switch (level) {
            case 3: return now.plusHours(2);
            case 2: return now.plusHours(6);
            default: return now.plusHours(24);
        }
    }

    private void writeEventLog(Long warningId, int eventType, Long operatorId, String content) {
        WarningEventLog log = new WarningEventLog();
        log.setWarningId(warningId);
        log.setEventType(eventType);
        log.setOperatorId(operatorId);
        log.setEventContent(content);
        warningEventLogMapper.insert(log);
    }

    private void writeTimelineForHandle(HealthWarning warning, String title, String content) {
        TimelineEvent event = new TimelineEvent();
        event.setElderId(warning.getElderId());
        event.setEventType(4);
        event.setEventTitle(title);
        event.setEventContent(content);
        event.setSourceType("warning");
        event.setSourceId(warning.getId());
        event.setEventTime(LocalDateTime.now());
        event.setDoctorId(warning.getDoctorId());
        timelineService.addEvent(event);
    }

    private void publish(String channel, Long targetUserId, String eventName, Object data) {
        if (targetUserId == null) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("targetUserId", targetUserId);
            payload.put("eventName", eventName);
            payload.put("data", data);
            String json = objectMapper.writeValueAsString(payload);
            // Redis 发布（用于分布式多实例推送）
            stringRedisTemplate.convertAndSend(channel, json);
            // 本地直接推送（避免 Redis pub/sub 在单机环境不可靠）
            JsonNode node = objectMapper.readTree(json);
            warningSseManager.pushToUser(
                node.get("targetUserId").asLong(),
                node.get("eventName").asText(),
                node.get("data")
            );
        } catch (Exception ignored) {
            // 推送失败不影响主流程
        }
    }
}
