# data 目录

- `resumes/`：按账号隔离的加密简历文件。
- `mysql/init.sql`：MySQL 默认初始化文件（建库、建表、默认管理员）。
- `postgresql/init.sql`：PostgreSQL 默认初始化文件（建库、建表、默认管理员）。

## 导入 MySQL

```bash
mysql -u root -p < data/mysql/init.sql
```

## 导入 PostgreSQL

```bash
psql -U postgres -f data/postgresql/init.sql
```

两个脚本都会创建默认工作区与 `admin / 123456` 账号。

## 连接配置

数据库连接不再通过设置页切换，请编辑 `jobtrack-web/src/main/resources/application-{profile}.yml`
中的 `spring.datasource`，并同步调整 `spring.sql.init.schema-locations`，重启服务后生效。
