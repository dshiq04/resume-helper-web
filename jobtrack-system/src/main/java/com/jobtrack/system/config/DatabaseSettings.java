package com.jobtrack.system.config;

import lombok.Data;

/**
 * 当前数据库连接信息（来自 application-{profile}.yml 中的 spring.datasource）。
 *
 * <p>只用于接口展示与连接测试，运行时不再写回配置文件；
 * 调整数据库请修改对应环境的 yml 后重启服务。</p>
 */
@Data
public class DatabaseSettings {

    private String type = "mysql";
    private String host = "127.0.0.1";
    private int port = 3306;
    private String name = "jobtrack";
    private String username = "";
    private String password = "";
    private String params = "";
    private int maxPoolSize = 10;
    /** 原始 JDBC URL。 */
    private String url = "";
    private String driver = "";

    public DatabaseSettings copy() {
        DatabaseSettings copy = new DatabaseSettings();
        copy.setType(type);
        copy.setHost(host);
        copy.setPort(port);
        copy.setName(name);
        copy.setUsername(username);
        copy.setPassword(password);
        copy.setParams(params);
        copy.setMaxPoolSize(maxPoolSize);
        copy.setUrl(url);
        copy.setDriver(driver);
        return copy;
    }
}
