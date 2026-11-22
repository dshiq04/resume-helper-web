package com.jobtrack.application.pojo.po;

import com.jobtrack.application.pojo.po.ApplicationRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 投递列表的缓存载体，避免分页对象的泛型在 JSON 反序列化时丢失元素类型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationPageCache {

    private List<ApplicationRecord> items;
    private long total;
    private int page;
    private int pageSize;
}
