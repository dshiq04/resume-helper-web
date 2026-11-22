package com.jobtrack.system.pojo.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户同意协议的记录，每个用户保留一条。
 *
 * <p>除签署时间外，还保留签署时的语言、签署人、地点、时区与来源 IP，
 * 用于管理员导出带有签署信息的协议存档。</p>
 */
@Data
@TableName("user_agreement")
public class UserAgreement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private LocalDateTime acceptedAt;

    /** 签署时界面语言：zh-CN / zh-TW / en / ja / ko / ru。 */
    private String language;

    /** 签署人名称，优先取个人资料中的真实姓名，否则取昵称。 */
    private String signerName;

    /** 签署地点，优先取个人资料中的城市，否则使用浏览器时区。 */
    private String location;

    /** 签署时浏览器时区，例如 Asia/Shanghai。 */
    private String timezone;

    /** 签署请求来源 IP。 */
    private String signedIp;
}
