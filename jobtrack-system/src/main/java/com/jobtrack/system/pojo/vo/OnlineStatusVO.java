package com.jobtrack.system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlineStatusVO {

    /** redis 或 memory。 */
    private String mode;
    private long onlineUsers;
    private boolean online;
}
