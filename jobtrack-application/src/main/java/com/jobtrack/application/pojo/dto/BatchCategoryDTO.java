package com.jobtrack.application.pojo.dto;

import com.jobtrack.common.constant.DomainConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class BatchCategoryDTO {

    @NotEmpty(message = "请至少选择一条记录")
    private List<Long> ids;

    @NotBlank(message = "请选择投递类型")
    @Pattern(regexp = DomainConstants.CATEGORY_PATTERN, message = "投递类型不存在")
    private String category;
}
