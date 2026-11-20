# 履途 ResumeTrail

一个自托管、多用户的求职投递管理服务。后端使用 **Spring Boot 4.2 + MyBatis-Plus**，前端使用 **React + Ant Design + Redux Toolkit**，通过 MyBatis-Plus 多租户拦截器按账号隔离数据。前端构建产物会直接写入后端静态资源目录，最终只需要启动一个 jar。

- 默认账号：`admin`
- 默认密码：`123456`
- 默认地址：`http://127.0.0.1:18080`

---

## 功能

### 投递管理

- **多年度投递**：每条记录带年度字段，列表、统计与 Excel 导入都支持按年度筛选，春招/秋招/实习/社招可以跨年归档。
- **只必填两项**：公司名称与投递链接必填，其余字段全部选填。
- **更完整的字段**：岗位、年度、状态、渠道、城市、薪资、联系人、联系方式、工作方式、优先级、面试轮次、下次跟进、备注。
- **置顶与内联编辑**：置顶记录优先展示；公司名称、投递链接、状态可以直接在表格中修改。
- **批量操作**：批量修改状态、投递类型、批量删除。
- **状态时间线**：初筛 → 笔试 → 面试 → Offer/淘汰，记录每个阶段的日期、间隔与停留天数；历史缺失时可以补全。
- **回收站**：删除先进入回收站，支持恢复、永久删除与清空。
- **企业名自动补全**：根据历史投递去重提示公司名称。
- **Excel 导入**：支持 `.xlsx` / `.xlsm`，内置中英双语模板和下拉选项，导入结果包含成功、跳过与错误明细。
- **浏览器扩展**：替代原桌面端的独立快速录入窗口，可在任意招聘页一键收录当前链接。

### 简历库

- 支持 PDF、DOCX、PPTX、PNG、JPG、WEBP、Markdown，单文件最大 50 MB。
- 简历名称、标签、置顶；文件使用 AES-256-GCM 加密落盘。
- 在线预览：PDF / 图片直接预览，DOCX 使用 mammoth 转换，Markdown 渲染，PPTX 提供下载。

### 数据库

- 默认使用 SQLite 文件数据库，零安装即可启动。
- 设置页支持在线测试并切换 SQLite / MySQL / PostgreSQL；切换会写回根目录 `config.json`，并自动初始化表结构与默认账号。
- 切换数据库后当前登录会退出，请使用目标数据库中的账号登录。

### 五种界面语言

- 简体中文、English、日本語、한국어、Русский，登录页与设置页都可以切换。

### 统计

- 累计投递、推进中、面试中、Offer 四项概览。
- 投递趋势、状态分布、阶段转化漏斗、各公司面试/Offer 对比、投递日历热力图。
- 支持年度、月度、季度、年度和自定义时间范围，可导出为 PNG。

### 账号与安全

- 多用户注册，每个账号拥有独立工作区，数据与其他账号完全隔离。
- Spring Security + JWT；登录态由服务端会话存储校验，支持退出登录、修改密码后强制下线。
- 密码使用 BCrypt 哈希保存。
- 可选的图形验证码（EasyCaptcha），验证码通过 Redis 一次性存储。**开启验证码必须同时开启 Redis**，否则服务会拒绝启动。
- 可选的闲置自动锁定。

### 可选 Redis

- `redis.enabled=false` 时：在线状态与会话使用进程内存储，列表缓存使用进程内缓存，零依赖启动。
- `redis.enabled=true` 时：
  - 登录会话与在线人数写入 Redis，多实例部署可共享；
  - 投递列表、统计、年度选项优先从 Redis 读取，未命中再查数据库；
  - 公共配置与账号偏好使用 Redis 缓存；
  - 支持图形验证码。
- Redis 临时不可用时会自动退回进程内存储，不影响登录与业务读写。

---

## 项目结构

```text
resume-helper-web/
├── config.json                 # 服务端全部运行配置（端口、数据库、Redis、验证码、JWT、存储）
├── pom.xml                     # 父 POM，只做依赖导入与版本收敛，并配置阿里云镜像
├── start.cmd                   # Windows 一键启动
├── start-macos.command         # macOS 一键启动
├── start-linux.sh              # Linux 一键启动
├── jobtrack-common/            # 通用返回结构、异常、租户上下文、JSON/时间工具
├── jobtrack-system/            # 用户、认证、JWT、租户拦截器、Redis、验证码、偏好设置
│   └── src/main/resources/mapper/    # SysUser / SysTenant / TenantSetting 的 XML
├── jobtrack-application/       # 投递、时间线、回收站、简历、统计、Excel、备份
│   └── src/main/resources/mapper/    # 投递 / 时间线 / 回收站 / 简历的 XML
├── jobtrack-web/               # Spring Boot 启动模块，托管前端静态资源
│   └── src/main/resources/
│       ├── application.yaml    # 只保留框架级默认值
│       ├── schema-sqlite.sql   # 默认 SQLite 建表脚本
│       ├── schema-mysql.sql    # MySQL 建表脚本
│       ├── schema-postgresql.sql # PostgreSQL 建表脚本
│       ├── schema-h2.sql       # 兼容选项 H2 建表脚本
│       └── static/             # 前端构建产物（自动生成）
├── frontend/                   # React + Ant Design + Redux Toolkit
│   └── src/
│       ├── api/                # 按后端 Controller 拆分的 axios 接口模块
│       ├── store/              # Redux Toolkit：auth / ui / meta / tabs / applications
│       ├── layouts/            # 侧边栏、顶部栏、可关闭页面标签栏
│       └── pages/              # 登录、投递、简历库、统计、回收站、设置、详情
└── extension/                  # Chrome / Edge 浏览器扩展（MV3）
```

### 前端请求拆分

`frontend/src/api/client.ts` 只保留 axios 实例、token、错误处理与通用请求方法。接口按后端 Controller 拆分：

| 文件 | 对应 Controller |
| --- | --- |
| `api/auth.ts` | `AuthController` |
| `api/applications.ts` | `ApplicationController` |
| `api/trash.ts` | `TrashController` |
| `api/resumes.ts` | `ResumeController` |
| `api/stats.ts` | `StatsController` |
| `api/excel.ts` | `ExcelController` |
| `api/backup.ts` | `BackupController` |
| `api/system.ts` | `SystemController` |
| `api/preferences.ts` | `PreferencesController` |
| `api/config.ts` | `PublicConfigController` |
| `api/quickEntry.ts` | `QuickEntryController` |

### 前端路由与状态

- 使用 React Router，页面路由包括 `/`、`/spring`、`/autumn`、`/internship`、`/social`、`/resumes`、`/statistics`、`/trash`、`/settings`、`/applications/:id`。
- 使用 Redux Toolkit 管理登录用户、主题、统计、在线状态、投递列表/筛选/分页、页面标签。
- 顶部提供可关闭的页面标签栏（Tag 多页面），页面切换带淡入上移动画。

---

## 一键启动

启动脚本会依次：检查环境 → 安装前端依赖 → 构建前端 → Maven 打包 → 启动服务 → 等待健康检查 → 打开浏览器。

### Windows

双击 `start.cmd`，或在命令行执行：

```bat
start.cmd
```

### macOS

```bash
chmod +x start-macos.command
./start-macos.command
```

### Linux

```bash
chmod +x start-linux.sh
./start-linux.sh
```

启动后访问脚本输出的地址，默认 `http://127.0.0.1:18080`。

停止后台服务：

```bash
kill $(cat .jobtrack.pid)
```

Windows 下可在任务管理器中结束对应的 `java` 进程。

### 手动启动

```bash
# 1. 前端
cd frontend
npm install
npm run build        # 产物直接写入 jobtrack-web/src/main/resources/static

# 2. 后端
cd ..
mvn -DskipTests package
java -jar jobtrack-web/target/jobtrack.jar
```

开发模式：

```bash
# 终端 1：后端
mvn -pl jobtrack-web -am spring-boot:run

# 终端 2：前端（Vite 代理 /api 到 18080）
cd frontend
npm run dev
```

---

## config.json

服务端配置全部位于项目根目录 `config.json`。应用启动时由
`JsonConfigEnvironmentPostProcessor` 读取并转为 Spring 属性，`application.yaml`
只保留框架级默认值。启动脚本从项目根目录运行，因此可以直接找到该文件。

```json
{
  "server": { "address": "127.0.0.1", "port": 18080 },
  "database": {
    "type": "sqlite",
    "file": "jobtrack.db",
    "host": "127.0.0.1",
    "port": 3306,
    "name": "jobtrack",
    "username": "sa",
    "password": "123456",
    "maxPoolSize": 10
  },
  "redis": {
    "enabled": false,
    "host": "127.0.0.1",
    "port": 6379,
    "password": "",
    "database": 0,
    "keyPrefix": "jobtrack:",
    "timeout": "2s"
  },
  "captcha": {
    "enabled": false,
    "type": "char",
    "length": 4,
    "width": 130,
    "height": 44,
    "ttlSeconds": 180
  },
  "app": {
    "dataDir": "./data",
    "logDir": "./log",
    "jwt": { "secret": "至少 32 位随机字符串", "ttlHours": 72 },
    "storage": { "secret": "至少 32 位随机字符串", "maxFileSizeMb": 50 },
    "admin": {
      "username": "admin",
      "password": "123456",
      "displayName": "管理员",
      "tenantName": "默认工作区"
    }
  },
  "logging": { "level": "INFO" }
}
```

说明：

- `database.type` 支持 `sqlite`（默认，无需安装）、`mysql`、`postgresql`，并保留 `h2` 作为兼容选项。
- 设置页提供「数据库连接」卡片，可测试连接并在线切换 SQLite / MySQL / PostgreSQL；切换成功后会写回 `config.json` 并退出当前登录。
- 切换 MySQL 后，请先创建数据库（默认 `jobtrack`，字符集 `utf8mb4`）；PostgreSQL 请先创建数据库（默认 `jobtrack`）。服务会自动执行对应的建表脚本，并给空库创建默认工作区与 `admin / 123456`。
- `app.dataDir` 存放 SQLite 文件、H2 数据库与加密简历文件；`app.logDir` 存放运行日志。
- SQLite 本身没有原生数据库密码；设置页中的密码会随 `config.json` 保存，登录密码仍为 `admin / 123456`。
- `app.admin` 只在首次启动时创建账号，不会覆盖已修改的密码。
- 修改 `config.json` 后重启服务生效；设置页切换数据库会立即生效并自动写回配置。
- 也可以用环境变量 `JOBTRACK_CONFIG` 指定其他配置文件路径。

### Redis 与验证码

- 先用默认配置启动（Redis 关闭），一切功能可用，只是在线状态与缓存使用进程内实现。
- 需要验证码时：
  1. 启动 Redis；
  2. 将 `redis.enabled` 改为 `true`，填写连接信息；
  3. 将 `captcha.enabled` 改为 `true`；
  4. 重启服务。
- 若只把 `captcha.enabled` 改为 `true` 而 Redis 仍为关闭状态，应用会直接拒绝启动并给出明确提示。这是有意设计，避免验证码无法校验的“假安全”。
- 验证码类型支持 `char`（字母数字）和 `math`（算术）。

---

## 浏览器扩展

`extension/` 是一个 Manifest V3 扩展，替代原桌面端的“独立快速录入窗口”。

安装方式（Chrome / Edge）：

1. 打开 `chrome://extensions` 或 `edge://extensions`；
2. 打开「开发者模式」；
3. 点击「加载已解压的扩展程序」，选择项目下的 `extension/` 目录；
4. 打开扩展设置，填写服务端地址（默认 `http://127.0.0.1:18080`），登录账号；
5. 在任意招聘页面点击扩展图标，或按 `Alt+Shift+J`，也可右键选择「加入履途 ResumeTrail」。

扩展会自动填入当前页面链接，并按页面标题猜测公司名称。公司名称与投递链接是必填项，其余字段可选。提交后窗口保持打开，方便连续录入。若服务端开启验证码，扩展登录页也会显示验证码。

---

## 数据库表

所有业务表都包含 `tenant_id`，由 MyBatis-Plus `TenantLineInnerInterceptor` 自动追加隔离条件。

| 表 | 说明 |
| --- | --- |
| `sys_tenant` | 工作区（租户） |
| `sys_user` | 登录账号，全局唯一用户名 |
| `tenant_setting` | 账号偏好，如闲置锁定 |
| `applications` | 投递记录，含年度 `apply_year` 与扩展字段 |
| `application_changes` | 状态时间线，唯一键 `(tenant_id, application_id, status)` |
| `deleted_applications` | 回收站快照 |
| `resumes` | 简历元数据，文件加密落盘 |

`sys_user` 与 `sys_tenant` 被租户拦截器排除，因为登录阶段还需要根据用户名定位账号；其余业务表全部自动隔离。

每个模块的 `src/main/resources/mapper/` 下都有与 Mapper 接口同名的 XML 文件，并在 `application.yaml` 中通过 `classpath*:/mapper/**/*.xml` 加载。

---

## 默认账号与注册

- 首次启动自动创建 `admin / 123456`，所属工作区为「默认工作区」。
- 登录页可直接注册新账号，注册后自动登录。
- 每个账号的数据、简历文件、统计与回收站完全独立。
- 修改密码后，旧会话会立即失效，需要重新登录。

---

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/health` | 健康检查 |
| `GET` | `/api/config` | 公共配置（选项、版本、验证码开关、在线存储类型） |
| `GET` | `/api/auth/status` | 登录状态 |
| `GET` | `/api/auth/captcha` | 获取图形验证码（需开启验证码） |
| `POST` | `/api/auth/login` | 登录 |
| `POST` | `/api/auth/register` | 注册并创建工作区 |
| `GET` | `/api/auth/me` | 当前用户 |
| `PUT` | `/api/auth/me` | 修改昵称 |
| `POST` | `/api/auth/change-password` | 修改密码 |
| `POST` | `/api/auth/logout` | 退出登录 |
| `GET` | `/api/auth/online` | 在线人数与存储类型 |
| `GET` | `/api/applications` | 投递列表，支持搜索/类型/状态/渠道/年度/日期/分页 |
| `GET` | `/api/applications/years` | 有记录的年度列表 |
| `POST` | `/api/applications` | 新增投递 |
| `GET` | `/api/application/{id}` | 投递详情 |
| `PUT` | `/api/application/{id}` | 局部更新 |
| `DELETE` | `/api/application/{id}` | 删除（进入回收站） |
| `GET/POST` | `/api/application/{id}/history` | 状态时间线 / 补全阶段 |
| `GET` | `/api/applications/companies` | 公司名补全 |
| `POST` | `/api/applications/import` | Excel 导入 |
| `GET` | `/api/applications/template` | 下载导入模板 |
| `GET` | `/api/trash` | 回收站列表 |
| `POST` | `/api/trash/{id}/restore` | 恢复 |
| `DELETE` | `/api/trash/{id}` | 永久删除 |
| `DELETE` | `/api/trash` | 清空回收站 |
| `GET/POST` | `/api/resumes` | 简历列表 / 上传 |
| `GET` | `/api/resume/{id}/file` | 读取（解密）简历文件 |
| `GET` | `/api/stats` | 概览统计 |
| `GET` | `/api/stats/daily` | 每日投递趋势 |
| `GET` | `/api/stats/funnel` | 转化漏斗 |
| `GET/PUT` | `/api/system/preferences` | 闲置锁定偏好 |
| `GET` | `/api/system/info` | 版本、数据库、配置文件、数据目录 |
| `GET` | `/api/system/database` | 当前数据库配置与支持的数据库类型 |
| `POST` | `/api/system/database/test` | 测试数据库连接 |
| `PUT` | `/api/system/database` | 保存并热切换数据库 |
| `GET` | `/api/system/backup/export` | 导出 ZIP 备份 |
| `POST` | `/api/system/backup/import` | 导入备份 |
| `GET` | `/api/system/quick-entry/defaults` | 扩展默认类型与服务器时间 |

除健康检查、公共配置与登录/注册/验证码外，`/api/**` 都需要：

```http
Authorization: Bearer <token>
```

---

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Spring Boot 4.2、Spring Security、Spring Data Redis |
| 数据访问 | MyBatis-Plus 3.5.18（多租户、分页） |
| 数据库 | SQLite（默认）/ MySQL 8 / PostgreSQL 16+（运行时切换） |
| 认证 | JWT（jjwt）+ 服务端会话校验 |
| 验证码 | EasyCaptcha |
| Excel | Apache POI |
| 前端 | React 18、TypeScript、Vite、Ant Design 5 |
| 状态管理 | Redux Toolkit + React Redux |
| 图表 | ECharts |
| 构建 | Maven 多模块，父 POM 仅导入依赖并配置阿里云镜像 |

---

## 注意事项

- 生产环境请修改 `config.json` 中的 `app.jwt.secret` 与 `app.storage.secret`。
- 默认监听 `127.0.0.1`，仅本机访问；如需局域网访问，将 `server.address` 改为 `0.0.0.0` 并自行配置防火墙/反向代理。
- 浏览器扩展与网页端必须连接同一个服务端地址。
- 数据库与简历文件都在 `data/` 下，备份时建议使用设置页的「导出备份」，或整体打包 `data/` 与 `config.json`。
