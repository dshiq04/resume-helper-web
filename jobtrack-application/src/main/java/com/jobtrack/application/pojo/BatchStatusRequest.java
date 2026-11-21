package com.jobtrack.application.pojo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchStatusRequest {

    @NotEmpty(message = "请至少选择一条记录")
    private List<Long> ids;

    private String status;
}
