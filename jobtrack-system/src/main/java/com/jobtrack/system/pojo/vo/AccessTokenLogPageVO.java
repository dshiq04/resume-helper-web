package com.jobtrack.system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessTokenLogPageVO {

    private List<AccessTokenLogVO> items;
    private long total;
    private int page;
    private int pageSize;
}
