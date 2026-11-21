# 履途 ResumeTrail

一个自托管、多用户的求职投递管理服务。后端使用 **Spring Boot 4.2 + MyBatis-Plus**，前端使用 **React + Ant Design + Redux Toolkit**，通过 MyBatis-Plus 多租户拦截器按账号隔离数据。前后端完全分离：后端只提供 REST 接口，前端构建产物输出到 `frontend/dist`，由 Nginx 等静态服务器托管。

- 默认账号：`admin`
- 默认密码：`123456`
- 默认地址：`http://127.0.0.1:18080`

---

## 功能

### 投递管理

- **多年度投递**：每条记录带年度字段，列表与统计都支持按年度筛选，春招/秋招/实习/社招可以跨年归档。
- **只必填两项**：公司名称与投递链接必填，其余字段全部选填。
- **更完整的字段**：岗位、年度、状态、渠道、城市、薪资、联系人、联系方式、工作方式、优先级、面试轮次、下次跟进、备注。
- **置顶与内联编辑**：置顶记录优先展示；公司名称、投递链接、状态可以直接在表格中修改。
- **批量操作**：批量修改状态、投递类型、批量删除。
- **状态时间线**：初筛 → 笔试 → 面试 → Offer/淘汰，记录每个阶段的日期、间隔与停留天数；历史缺失时可以补全。
- **回收站**：删除先进入回收站，支持恢复、永久删除与清空。
- **企业名自动补全**：根据历史投递去重提示公司名称。
- **浏览器扩展**：替代原桌面端的独立快速录入窗口，可在任意招聘页一键收录当前链接。

### 简历库

- 支持 PDF、DOCX、PPTX、PNG、JPG、WEBP、Markdown，单文件最大 50 MB。
- 简历名称、标签、置顶；文件使用 AES-256-GCM 加密落盘。
- 在线预览：PDF / 图片直接预览，DOCX 使用 mammoth 转换，Markdown 渲染，PPTX 提供下载。

### 数据库

- 支持 MySQL 8 与 PostgreSQL 16+，不再内置 SQLite。
- 连接信息位于 `application-{profile}.yml` 的 `spring.datasource`，dev / test / prod 各自独立。
- 服务启动时自动执行对应建表脚本，并在空库中创建默认工作区与 `admin / 123456`。
- 设置页的「数据库连接」只做信息展示与连通性测试；修改数据库请编辑对应环境文件后重启。

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
├── pom.xml                     # 父 POM，只做依赖导入与版本收敛，并配置阿里云镜像
├── jobtrack-common/            # 通用返回结构、异常、租户上下文、JSON/时间工具
├── jobtrack-system/            # 用户、认证、JWT、租户拦截器、Redis、验证码、偏好设置
│   └── src/main/resources/mapper/    # SysUser / SysTenant / TenantSetting 的 XML
├── jobtrack-application/       # 投递、时间线、回收站、简历、统计
│   └── src/main/resources/mapper/    # 投递 / 时间线 / 回收站 / 简历的 XML
├── jobtrack-web/               # Spring Boot 启动模块，只提供 REST 接口
│   └── src/main/resources/
│       ├── application.yaml    # 通用配置，默认激活 dev
│       ├── application-dev.yml # 开发环境：本机 MySQL + Redis
│       ├── application-test.yml # 测试环境
│       ├── application-prod.yml # 生产环境：PostgreSQL
│       ├── schema-mysql.sql    # MySQL 建表脚本
│       └── schema-postgresql.sql # PostgreSQL 建表脚本
├── frontend/                   # React + Ant Design + Redux Toolkit（产物在 dist/）
│   └── src/
│       ├── api/                # 按后端 Controller 拆分的 axios 接口模块
│       ├── store/              # Redux Toolkit：auth / ui / meta / tabs / applications
│       ├── layouts/            # 侧边栏、顶部栏、可关闭页面标签栏
│       ├── styles/             # 按模块拆分的 SCSS，入口 index.scss
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
| `api/system.ts` | `SystemController` |
| `api/preferences.ts` | `PreferencesController` |
| `api/config.ts` | `PublicConfigController` |
| `api/quickEntry.ts` | `QuickEntryController` |

### 前端样式（Sass）

`frontend/src/styles/` 使用 Sass 并按模块拆分，由 `index.scss` 统一引入：

| 文件 | 模块 |
| --- | --- |
| `_base.scss` | 主题变量、全局重置、通用工具类 |
| `_layout.scss` | 侧边栏、顶部栏、品牌区 |
| `_tabs.scss` | 页面标签栏与页面切换动画 |
| `_dashboard.scss` | 投递列表与总览 |
| `_login.scss` | 登录 / 注册页 |
| `_forms.scss` | 表单、抽屉与弹窗 |
| `_detail.scss` | 投递详情 |
| `_resumes.scss` | 简历库 |
| `_statistics.scss` | 统计分析 |
| `_settings.scss` | 设置页 |
| `_misc.scss` | 启动屏与 Ant Design 细节覆盖 |
| `_responsive.scss` | 响应式断点（复用 `respond-down` mixin） |

尺寸、断点、字体与动效常量位于 `_variables.scss`，省略号、卡片、响应式等复用片段位于 `_mixins.scss`。

### 前端路由与状态

- 使用 React Router，页面路由包括 `/`、`/spring`、`/autumn`、`/internship`、`/social`、`/resumes`、`/statistics`、`/trash`、`/settings`、`/applications/:id`。
- 使用 Redux Toolkit 管理登录用户、主题、统计、在线状态、投递列表/筛选/分页、页面标签。
- 顶部提供可关闭的页面标签栏（Tag 多页面），页面切换带淡入上移动画。

---

## 启动方式

### 手动启动

```bash
# 1. 前端（产物输出到 frontend/dist）
cd frontend
npm install
npm run build

# 2. 后端（默认激活 dev，数据库账号见 application-dev.yml）
cd ..
mvn -DskipTests package
java -jar jobtrack-web/target/jobtrack.jar
```

切换环境：

```bash
java -jar jobtrack-web/target/jobtrack.jar --spring.profiles.active=prod
# 或
export SPRING_PROFILES_ACTIVE=test
```

### 手动启动

开发模式：

```bash
# 终端 1：后端
mvn -pl jobtrack-web -am spring-boot:run

# 终端 2：前端（Vite 代理 /api 到 18080）
cd frontend
npm run dev
```

---

## 前端 .env

前端构建环境变量位于 `frontend/.env`：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `VITE_APP_TITLE` | 浏览器标题 | `履途 ResumeTrail` |
| `VITE_APP_VERSION` | 前端兜底版本号 | `1.2` |
| `VITE_APP_DEFAULT_LANGUAGE` | 默认界面语言 | `zh-CN` |
| `VITE_SHOW_DEVELOPMENT_WATERMARK` | 是否显示「开发中品质，请谅解」水印 | `true` |
| `VITE_API_BASE_URL` | 后端接口地址 | `/api` |

环境覆盖：

- `.env.development`：开发环境，`VITE_API_BASE_URL=/api`，Vite 代理到 `http://127.0.0.1:18080`
- `.env.test`：测试环境，默认连接 `http://127.0.0.1:18081/api`
- `.env.production`：生产环境，默认同源 `/api`（由 Nginx 反向代理到后端）；前后端不同域时改为后端完整地址

```bash
npm run dev            # 开发模式
npm run build:test     # 测试环境构建
npm run build          # 生产构建
```

Ant Design 的 `ConfigProvider` 与 dayjs locale 会随设置中的语言同步切换，水印由 `VITE_SHOW_DEVELOPMENT_WATERMARK` 控制。

---

## 环境配置（application-{profile}.yml）

服务端配置不再使用 `config.json`，全部转移到了 YAML：

| 文件 | 用途 |
| --- | --- |
| `application.yaml` | 通用配置：MyBatis-Plus、上传大小、Redis 仓库关闭等 |
| `application-dev.yml` | 开发环境，默认激活：本机 MySQL + 本机 Redis |
| `application-test.yml` | 测试环境：独立测试库 + 独立 Redis 库 |
| `application-prod.yml` | 生产环境：默认 PostgreSQL，密钥必须由环境变量提供 |

主要配置项（以 dev 为例）：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/${DB_NAME:jobtrack}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
  sql:
    init:
      schema-locations: classpath:schema-mysql.sql
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}

redis:
  enabled: true
captcha:
  enabled: true

app:
  data-dir: ./data
  log-dir: ./log
  jwt:
    secret: ${JWT_SECRET:...}
    ttl-hours: 72
  storage:
    secret: ${STORAGE_SECRET:...}
    max-file-size-mb: 50
  admin:
    username: admin
    password: 123456
```

说明：

- 数据库只支持 `mysql` 与 `postgresql`，切换方式：替换 `spring.datasource` 配置块，并把 `spring.sql.init.schema-locations` 改成对应的 `schema-*.sql`；每个配置文件里都附带了另一种数据库的注释示例。
- 数据库账号密码可以直接改 yml，也可以用 `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` 等环境变量覆盖。
- 请先创建数据库（MySQL 默认 `jobtrack`，字符集 `utf8mb4`；PostgreSQL 默认 `jobtrack`）。服务启动时自动建表，并在空库中创建 `admin / 123456`。
- `app.data-dir` 存放加密简历文件等运行数据，`app.log-dir` 存放日志。
- `app.admin` 只在首次启动时创建账号，不会覆盖已修改的密码。
- 设置页的「数据库连接」为只读展示 + 连通性测试；修改配置后重启服务生效。
- 生产环境必须提供 `JWT_SECRET` 与 `STORAGE_SECRET` 环境变量，否则启动失败。

### Redis 与验证码

- dev / test / prod 三个环境默认都开启 Redis 与图形验证码（`redis.enabled: true`、`captcha.enabled: true`）。
- 启动前请确保 Redis 可用；验证码写入 Redis 失败时会返回 503，在线状态会自动退回进程内存储。
- 如确实不需要，可在对应 yml 中把二者改为 `false`（验证码依赖 Redis，不能只开验证码不开 Redis，否则服务拒绝启动）。
- 验证码类型支持 `char`（字母数字）和 `math`（算术）。

---

## 前端部署

前端构建产物位于 `frontend/dist`，可以放到任意静态服务器。Nginx 同域部署示例：

```nginx
server {
    listen 80;
    server_name resume.example.com;

    root /var/www/resume-helper/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:18080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

若前后端不同域，构建前把 `.env.production` 中的 `VITE_API_BASE_URL` 改为后端完整地址，并在 `application-prod.yml` 的 `CORS_ALLOWED_ORIGINS` 中登记前端域名。

---

## 浏览器扩展

`extension/` 是一个 Manifest V3 扩展，替代原桌面端的“独立快速录入窗口”。

安装方式（Chrome / Edge）：

1. 打开 `chrome://extensions` 或 `edge://extensions`；
2. 打开「开发者模式」；
3. 点击「加载已解压的扩展程序」选择 `extension/` 目录，或直接把根目录的 `extension.crx` 拖入扩展管理页；
4. 打开扩展，填写服务器、端口与授权码（在网页端「账户设置 → 授权码」生成，需要读或读写权限）；
5. 在任意招聘页面点击扩展图标，或按 `Alt+Shift+J`，也可右键选择「加入履途 ResumeTrail」。

扩展会自动填入当前页面链接，并按页面标题猜测公司名称。公司名称与投递链接是必填项，其余字段可选。

- 每次打开和提交前都会调用 `/api/tokens/validate` 校验授权码：不存在、已过期、已停用或账号异常时禁止录入并提示原因。
- 只读授权码可以连接查看，但无法录入；录入需要使用读写授权码。
- 扩展使用授权码作为 Bearer 凭证访问接口，服务端按 `SCOPE_read` / `SCOPE_write` 在 Spring Security 中限制读写。
- `extension.crx` 由 `extension/` 目录打包生成；更新扩展需要保留签名密钥 `extension.pem`（已在 `.gitignore` 中忽略，请自行备份）。

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
| `user_profile` | 账号扩展个人资料 |
| `access_token` | 授权码（权限类型、数据范围、有效期） |
| `access_token_log` | 授权码每次校验的使用日志（结果、权限、范围、IP、UA） |

`sys_user` 与 `sys_tenant` 被租户拦截器排除，因为登录阶段还需要根据用户名定位账号；其余业务表全部自动隔离。

每个模块的 `src/main/resources/mapper/` 下都有与 Mapper 接口同名的 XML 文件，并在 `application.yaml` 中通过 `classpath*:/mapper/**/*.xml` 加载。

---

## 默认账号与注册

- 首次启动自动创建 `admin / 123456`，所属工作区为「默认工作区」。
- 登录页可直接注册新账号，注册后自动登录。
- 每个账号的数据、简历文件、统计与回收站完全独立。
- 修改密码后，旧会话会立即失效，需要重新登录。

---

## 账户设置与授权码

点击左下角头像或昵称进入「账户设置」（`/account/profile`、`/account/tokens` 二级路由）：

- **个人资料**：头像（预留）、用户名、密码，以及基本信息 / 求职意向 / 教育背景 / 链接与简介等扩展资料，独立存放在 `user_profile` 表。
- **授权码**：为浏览器扩展生成访问码，可选择权限类型（`read` / `write` / `readwrite`）和数据范围（投递、简历、统计、个人资料），支持有效期或长期有效、启用/停用与逻辑删除；页面下方展示最近 50 条使用记录，可按结果筛选。

扩展或第三方工具可调用无需登录的校验接口：

```http
POST /api/tokens/validate
Content-Type: application/json

{ "code": "rt_xxx" }
```

返回 `valid`、`reason`（invalid / expired / disabled）、`scope`、`resources`、`expiresOn` 以及账号的 `username`、`displayName`。每次调用（含失败）都会记录使用日志，并刷新授权码的最近使用时间。

- 校验失败时返回对应状态码：`401` 不存在 / 已删除，`403` 已停用或账号异常，`410` 已过期。
- 授权码生成、启用/停用、删除时会同步写入 Redis Hash（`jobtrack:access-code:<code>`），校验与接口鉴权优先从 Redis 读取；缓存未命中视为无效，启动时会全量同步数据库中的授权码。

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
| `GET` | `/api/system/info` | 版本、数据库、运行环境、数据目录 |
| `GET` | `/api/system/database` | 当前数据库配置、运行环境与配置文件（只读） |
| `GET` | `/api/system/database/test` | 测试当前数据库连接 |
| `GET` | `/api/system/quick-entry/defaults` | 扩展默认类型与服务器时间 |
| `GET/PUT` | `/api/account/profile` | 读取 / 保存个人资料 |
| `PUT` | `/api/account/username` | 修改用户名 |
| `GET/POST` | `/api/account/tokens` | 授权码列表 / 生成授权码 |
| `PUT` | `/api/account/tokens/{id}/enabled` | 启用 / 停用授权码 |
| `DELETE` | `/api/account/tokens/{id}` | 逻辑删除授权码（数据库保留） |
| `GET` | `/api/account/tokens/logs?limit=50&result=` | 授权码使用日志（默认最近 50 条，可按结果过滤） |
| `POST` | `/api/tokens/validate` | 校验授权码（无需登录） |

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
| 数据库 | MySQL 8 / PostgreSQL 16+（按环境配置） |
| 认证 | JWT（jjwt）+ 服务端会话校验 |
| 验证码 | EasyCaptcha |
| 前端 | React 18、TypeScript、Vite、Ant Design 5 |
| 状态管理 | Redux Toolkit + React Redux |
| 图表 | ECharts |
| 构建 | Maven 多模块，父 POM 仅导入依赖并配置阿里云镜像 |

---

## 注意事项

- 生产环境请通过环境变量提供 `JWT_SECRET` 与 `STORAGE_SECRET`；dev / test 的默认密钥仅供本地使用。
- dev 默认监听 `127.0.0.1`，test / prod 默认监听 `0.0.0.0`，请按需配置防火墙与反向代理。
- 浏览器扩展与网页端必须连接同一个服务端地址。
- 简历文件在 `data/` 下、业务数据在数据库中，备份时请同时备份 `data/` 与数据库。
