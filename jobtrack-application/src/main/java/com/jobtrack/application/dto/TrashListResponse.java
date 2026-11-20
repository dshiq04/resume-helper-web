package com.jobtrack.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrashListResponse {

    private List<TrashItem> items;
    private long total;
}
