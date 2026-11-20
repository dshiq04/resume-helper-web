package com.jobtrack.system.dto;

import lombok.Data;

@Data
public class PreferencesRequest {

    private Boolean idleLockEnabled;
    private Integer idleLockMinutes;
}
