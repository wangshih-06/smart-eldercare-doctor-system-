package com.medical.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.entity.HealthWarning;

import java.util.Map;

/**
 * 健康预警服务接口
 */
public interface WarningService {

    Page<HealthWarning> list(Integer pageNum, Integer pageSize, Integer status, Integer warningLevel, Long elderId);

    HealthWarning getDetail(Long id);

    void handle(Long id, String handleResult, Long doctorId);

    void ignore(Long id, String handleResult);

    Long create(HealthWarning warning);

    /**
     * 创建预警并实时推送给责任医生（核心入口）：
     * 写库 -> 写生命周期日志 -> 计算处理截止时间 -> 通过 Redis 发布/订阅推送给在线医生端
     */
    Long createAndPush(HealthWarning warning);

    /**
     * 标记预警为已读
     */
    void markRead(Long id, Long operatorId);

    /**
     * 实时统计：未读数、高危未处理数、今日新增数
     */
    Map<String, Object> getRealtimeStats(Long doctorId);

    Map<String, Object> getStats();
}
