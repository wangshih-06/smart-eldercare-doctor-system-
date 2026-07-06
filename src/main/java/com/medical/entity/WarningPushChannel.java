package com.medical.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预警推送在线连接记录实体
 */
@Data
@TableName("warning_push_channel")
public class WarningPushChannel implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 通道类型: 1医生 2管理员 */
    private Integer channelType;

    private String clientId;

    private LocalDateTime lastActiveTime;

    /** 状态: 0离线 1在线 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
