package com.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medical.common.push.WarningSseManager;
import com.medical.entity.WarningPushChannel;
import com.medical.mapper.WarningPushChannelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 预警实时推送流控制器（SSE）
 */
@RestController
@RequestMapping("/api/warnings")
public class WarningStreamController {

    @Autowired
    private WarningSseManager warningSseManager;

    @Autowired
    private WarningPushChannelMapper warningPushChannelMapper;

    @GetMapping("/stream")
    public SseEmitter stream(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        SseEmitter emitter = warningSseManager.register(userId);
        registerChannel(userId, request.getSession().getId());
        return emitter;
    }

    private void registerChannel(Long userId, String clientId) {
        WarningPushChannel existing = warningPushChannelMapper.selectOne(
                new LambdaQueryWrapper<WarningPushChannel>().eq(WarningPushChannel::getClientId, clientId));
        if (existing != null) {
            existing.setUserId(userId);
            existing.setStatus(1);
            existing.setLastActiveTime(LocalDateTime.now());
            warningPushChannelMapper.updateById(existing);
            return;
        }
        WarningPushChannel channel = new WarningPushChannel();
        channel.setUserId(userId);
        channel.setChannelType(1);
        channel.setClientId(clientId);
        channel.setLastActiveTime(LocalDateTime.now());
        channel.setStatus(1);
        warningPushChannelMapper.insert(channel);
    }
}
