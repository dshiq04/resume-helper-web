# data 目录

- `jobtrack.db`：默认 SQLite 数据库，由服务首次启动时自动创建。
- `jobtrack-test.db` 等：切换数据库时生成的文件，可自行删除。
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

## 在应用内切换

启动应用后进入「设置 → 数据库连接」，选择 MySQL / PostgreSQL，填写连接信息，先测试连接再保存切换。
