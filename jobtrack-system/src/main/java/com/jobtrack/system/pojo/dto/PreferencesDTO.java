package com.jobtrack.system.pojo.dto;

import lombok.Data;

@Data
public class PreferencesDTO {

    private Boolean idleLockEnabled;
    private Integer idleLockMinutes;
}
