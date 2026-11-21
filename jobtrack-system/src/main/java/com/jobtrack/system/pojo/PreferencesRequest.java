package com.jobtrack.system.pojo;

import lombok.Data;

@Data
public class PreferencesRequest {

    private Boolean idleLockEnabled;
    private Integer idleLockMinutes;
}
