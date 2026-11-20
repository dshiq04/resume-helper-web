package com.jobtrack.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportResult {

    private int imported;
    private int skipped;
    private int total;
    private List<String> errors;

    public static ImportResult of(int imported, int skipped, List<String> errors) {
        return new ImportResult(imported, skipped, imported + skipped, errors);
    }
}
