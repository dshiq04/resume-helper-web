package com.jobtrack.system.dto;

import com.jobtrack.system.config.DatabaseSettings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseSwitchResult {

    private boolean ok;
    private String warning;
    private DatabaseSettings settings;
}
