package com.jobtrack.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * config.json 中 database 节点的绑定对象。
 *
 * <p>支持 sqlite / mysql / postgresql（保留 h2 作为兼容选项）。
 * 设置页保存后会把新配置写回 config.json，并立即切换连接池。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "database")
public class DatabaseSettings {

    private String type = "sqlite";
    private String file = "jobtrack.db";
    private String host = "127.0.0.1";
    private int port = 3306;
    private String name = "jobtrack";
    private String username = "sa";
    private String password = "";
    private String params = "";
    private int maxPoolSize = 10;
    /** 直接指定 JDBC URL 时优先使用。 */
    private String url = "";
    private String driver = "";

    public DatabaseSettings copy() {
        DatabaseSettings copy = new DatabaseSettings();
        copy.setType(type);
        copy.setFile(file);
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
