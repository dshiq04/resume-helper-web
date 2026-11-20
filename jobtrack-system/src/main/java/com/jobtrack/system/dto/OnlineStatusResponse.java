package com.jobtrack.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlineStatusResponse {

    /** redis 或 memory。 */
    private String mode;
    private long onlineUsers;
    private boolean online;
}
