package com.jobtrack.application.pojo;

import com.jobtrack.common.constant.DomainConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 局部更新：null 表示不修改该字段。
 */
@Data
public class ApplicationUpdateRequest {

    @NotBlank(message = "请选择投递类型")
    @Pattern(regexp = DomainConstants.CATEGORY_PATTERN, message = "投递类型不存在")
    private String category;

    @NotBlank(message = "请输入公司名称")
    @Size(max = 255, message = "公司名称不能超过 255 个字符")
    private String companyName;

    @NotBlank(message = "请输入投递链接")
    @Size(max = 2048, message = "投递链接不能超过 2048 个字符")
    private String link;

    @NotNull(message = "请选择年度")
    @Positive(message = "年度必须为正整数")
    private Integer year;

    @Size(max = 255, message = "岗位名称不能超过 255 个字符")
    private String position;

    @NotBlank(message = "请选择状态")
    @Pattern(regexp = DomainConstants.STATUS_PATTERN, message = "投递状态不存在")
    private String status;

    @Size(max = 64, message = "投递渠道不能超过 64 个字符")
    private String channel;

    @Size(max = 128, message = "城市不能超过 128 个字符")
    private String city;

    @Size(max = 128, message = "薪资范围不能超过 128 个字符")
    private String salary;

    @Size(max = 128, message = "联系人不能超过 128 个字符")
    private String contact;

    @Size(max = 255, message = "联系方式不能超过 255 个字符")
    private String contactInfo;

    @Size(max = 32, message = "工作方式不能超过 32 个字符")
    private String workMode;

    @Size(max = 16, message = "优先级不能超过 16 个字符")
    private String priority;

    private Integer interviewRound;

    private LocalDateTime nextActionAt;

    @Size(max = 2000, message = "备注不能超过 2000 个字符")
    private String notes;

    private Boolean pinned;
}
