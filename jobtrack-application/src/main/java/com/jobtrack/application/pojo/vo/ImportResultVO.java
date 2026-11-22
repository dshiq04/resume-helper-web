package com.jobtrack.application.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Excel 导入结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultVO {

    private int imported;
    private int skipped;
    private int total;
    private List<String> errors;
}
