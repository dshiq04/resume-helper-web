package com.jobtrack.application.pojo;

import com.jobtrack.common.constant.DomainConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class BatchStatusRequest {

    @NotEmpty(message = "请至少选择一条记录")
    private List<Long> ids;

    @NotBlank(message = "请选择状态")
    @Pattern(regexp = DomainConstants.STATUS_PATTERN, message = "投递状态不存在")
    private String status;
}
