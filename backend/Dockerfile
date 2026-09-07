# =====================================================================
# CRM 后端镜像（多阶段构建：Maven 构建 → JRE 17 运行）
# 构建上下文 = backend/：docker build -t crm-backend .
# 或在 deploy/ 目录：docker compose build backend
# =====================================================================

# ---- 构建阶段：Maven + JDK 17（阿里云镜像加速依赖下载） ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 层缓存：先拷 pom 拉依赖，再拷源码
RUN mkdir -p /root/.m2 && printf '%s\n' \
      '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' \
      > /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# ---- 运行阶段：仅 JRE，非 root 运行 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --no-create-home crm
COPY --from=build /build/target/crm-backend-*.jar app.jar
USER crm

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
