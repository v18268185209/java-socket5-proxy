# ========== 构建阶段 ==========
FROM eclipse-temurin:17-jre-alpine AS builder

WORKDIR /app

# 安装 Maven 和 Node.js
RUN apk add --no-cache maven nodejs npm

# 复制 Maven 配置文件，先下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制后端源码并构建
COPY src ./src
RUN mvn package -DskipTests -B

# 复制前端文件
COPY web-ui/package.json web-ui/package-lock.json* ./web-ui/
COPY web-ui/vite.config.js ./web-ui/
COPY web-ui/index.html ./web-ui/
COPY web-ui/src ./web-ui/src/

# 安装前端依赖并构建
RUN cd web-ui && npm install && npm run build

# ========== 运行阶段 ==========
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 创建非 root 用户
RUN addgroup -g 1000 -S proxyhub && \
    adduser -u 1000 -S proxyhub -G proxyhub

# 从构建阶段复制 JAR
COPY --from=builder /app/target/*.jar app.jar

# 创建必要目录并设置权限
RUN mkdir -p /app/logs /app/conf /app/data && \
    chown -R proxyhub:proxyhub /app

USER proxyhub

# 暴露端口
EXPOSE 9090 1080 8080

# JVM 参数
ENV JAVA_OPTS="-Xmx512m -Xms128m -XX:+UseG1GC"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --config.path=/app/conf/application.yml"]
