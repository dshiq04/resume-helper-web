# =====================================================================
# 履途 ResumeTrail 后端运行镜像
# 基础镜像：Alpine 版 JRE 21，官方镜像中体积最小的一档
# （压缩约 70MB，解压约 170MB，远小于 temurin 默认 ubuntu/debian 版本）
# 说明：构建由 GitLab CI 使用 maven:3.9-eclipse-temurin-21 完成，
#       本镜像只负责运行，因此无需 JDK。
# =====================================================================
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

# 后端产物由 CI 构建后放在构建上下文中（上下文为仓库根目录）
COPY jobtrack-web/target/jobtrack.jar /app/jobtrack.jar

# 数据与日志目录（由宿主机目录挂载进来）
RUN mkdir -p /app/data /app/log

EXPOSE 18080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/jobtrack.jar"]
