# 履途 ResumeTrail GitLab CI 部署说明

## 目录结构

```
deploy/
├── Dockerfile                    # 后端运行镜像（eclipse-temurin:21-jre-alpine，官方最小 JRE 21）
├── nginx/
│   └── resume-helper.conf        # 前端 nginx 配置（CI 直接 docker cp 进 nginx 容器）
└── README.md
```

## 环境要求（openEuler 虚拟机）

| 组件 | 要求 |
|---|---|
| GitLab | Docker 容器部署 |
| GitLab Runner | Docker 容器部署，executor = docker，标签 `docker` |
| PostgreSQL / Redis | 运行在虚拟机上（后端容器用 `--network host` 访问 127.0.0.1） |
| nginx 容器 | 名称为 `jobtrack-nginx`，需暴露 81 端口（`-p 81:81`，见下方注意） |

### Runner 关键配置

编辑 `/srv/gitlab-runner/config/config.toml`（宿主机挂载出来的路径），给对应 runner 加上 docker.sock：

```toml
[runners.docker]
  volumes = ["/var/run/docker.sock:/var/run/docker.sock", "/cache"]
```

改完执行 `docker restart gitlab-runner` 生效。

### nginx 容器端口说明

`deploy/nginx/resume-helper.conf` 中写的是 `listen 81`，因此容器启动应为：

```bash
docker run -d --name jobtrack-nginx -p 81:81 -d nginx:stable-alpine
```

若现有容器是 `-p 81:80` 的映射，请把配置里的 `listen 81` 改成 `listen 80`。

## 必须在 GitLab 中配置的变量

项目 → Settings → CI/CD → Variables（勾选 Masked）：

| 变量 | 必填 | 说明 |
|---|---|---|
| `JWT_SECRET` | ✅ | JWT 签名密钥，缺失后端启动失败 |
| `STORAGE_SECRET` | ✅ | 文件存储加密密钥 |
| `DB_PASSWORD` | 建议 | PostgreSQL 密码 |
| `REDIS_PASSWORD` | 建议 | Redis 密码 |
| `CORS_ALLOWED_ORIGINS` | 可选 | 前端站点地址，同域部署可留默认 `*` |

## 流水线各阶段做了什么

1. **cleanup-old**：删除旧后端容器/镜像；删除宿主机 `/opt/resume-helper/frontend/resume-helper` 旧前端文件
2. **build-backend / build-frontend**（并行）：`docker build` 多阶段构建后端镜像（镜像内 Maven 打包 `jobtrack.jar`，jar 不再上传 artifact）；Vite 构建 `dist`
3. **deploy-backend**：直接启动 build-backend 产出的镜像（挂载 `/opt/resume-helper/backend/{data,log}`）→ 健康检查
4. **deploy-frontend**：`dist` 复制到宿主机并重命名为 `resume-helper` → `docker cp` 进 nginx 容器 → 写入 nginx 配置 → `nginx -t` 校验 → `docker restart`
5. **cleanup-node_modules**：清理 `jobtrack-ui/node_modules`

## 常用修改点

- 后端镜像名/容器名/nginx 容器名：改 `.gitlab-ci.yml` 顶部 `variables`
- 前端部署目录：`DEPLOY_HOST_DIR`（默认 `/opt/resume-helper`）
- nginx 代理后端地址：`BACKEND_UPSTREAM`（默认 `http://172.17.0.1:18080`，即 docker0 网关；nginx 容器为 host 网络时改为 `http://127.0.0.1:18080`）
- 其他分支触发：把 `workflow.rules` 中的 main 限制放开
