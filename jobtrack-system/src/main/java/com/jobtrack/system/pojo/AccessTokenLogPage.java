package com.jobtrack.system.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessTokenLogPage {

    private List<AccessTokenLogView> items;
    private long total;
    private int page;
    private int pageSize;
}
