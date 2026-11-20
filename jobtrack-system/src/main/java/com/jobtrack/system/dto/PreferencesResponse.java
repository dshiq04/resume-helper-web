package com.jobtrack.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreferencesResponse {

    private boolean idleLockEnabled;
    private int idleLockMinutes;
}
