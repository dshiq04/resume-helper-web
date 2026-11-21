package com.jobtrack.application.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeListResponse {

    private List<ResumeView> items;
    private long total;
}
