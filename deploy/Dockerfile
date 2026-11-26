# =====================================================================
# 履途 ResumeTrail 后端镜像（多阶段构建）
#
# 阶段 1（builder）：JDK 21 + Maven，在镜像内完成打包
# 阶段 2（运行时）：Alpine 版 JRE 21，官方镜像中体积最小的一档
#                   （压缩约 70MB，解压约 170MB）
#
# 为什么在镜像里构建？
#   CI 直接把本文件 docker build 成最终镜像，jar 全程留在 docker 守护进程
#   里，不再作为 GitLab artifact 上传，彻底避免 "413 Request Entity Too
#   Large"（jobtrack.jar 约 110MB，超过 GitLab 默认 100MB 上限）。
#
# 构建上下文为仓库根目录：docker build -f deploy/Dockerfile -t <tag> .
# 依赖缓存使用 BuildKit cache mount（需 DOCKER_BUILDKIT=1，Engine 23+ 默认开启）
# =====================================================================

# ------------------------- 构建阶段 -------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 全量复制（.dockerignore 已排除 jobtrack-ui/node_modules/target 等无关内容）
COPY . .

# Maven 本地仓库挂到 BuildKit 缓存，跨次构建复用，避免每次重新下载依赖
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp -DskipTests package

# ------------------------- 运行阶段 -------------------------
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="resume-helper" \
      description="履途 ResumeTrail 后端（Spring Boot 4.2 / JRE 21 Alpine）"

# 通过 JAVA_OPTS 强制指定生产 profile，避免环境变量被误覆盖。
ENV TZ=Asia/Shanghai \
    LANG=zh_CN.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dspring.profiles.active=prod"

# 时区数据（Alpine 镜像默认不含 tzdata）
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo "${TZ}" > /etc/timezone \
    && apk del tzdata

WORKDIR /app

# 只把构建阶段产出的 jar 拷进来，源码与 Maven 依赖不进最终镜像
COPY --from=builder /build/jobtrack-web/target/jobtrack.jar /app/jobtrack.jar

# 数据与日志目录（由宿主机目录挂载进来）
RUN mkdir -p /app/data /app/log

EXPOSE 18080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/jobtrack.jar"]
