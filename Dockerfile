# Ainer Boot 开发环境 Dockerfile（多阶段构建）
#
# 用途：为 docker-compose 开发环境构建可运行 JAR。
#   - 这是开发便捷镜像，不是发布制品。生产发布仍走 Maven Wrapper + release profile。
#   - 构建阶段使用仓库内 Maven Wrapper（Maven 4.0.0-rc-6），遵守 AGENTS.md 生产者构建规则。
#
# 构建参数：
#   AINER_MODULE — 目标可执行模块（ainer-server 或 ainer-authorization-server）
#
# 用法：
#   docker build --build-arg AINER_MODULE=ainer-server -t ainer-server:dev .
#   docker build --build-arg AINER_MODULE=ainer-authorization-server -t ainer-authorization-server:dev .
#
# 注意：不使用 BuildKit 的 --mount=type=cache（该特性需要 buildx 组件，部分环境如 Colima 默认
# docker daemon 不提供 buildx）。改为先 COPY 全部 pom 再 dependency:go-offline 预热依赖，
# 这样源码变更时 Maven 依赖层可命中 Docker 层缓存，避免每次重新下载。

ARG AINER_MODULE=ainer-server

# ---- 构建阶段：JDK 25 + Maven Wrapper ----
FROM eclipse-temurin:25-jdk-noble AS builder

ARG AINER_MODULE

WORKDIR /build

# 先复制 Maven Wrapper 与全部 pom.xml，预热依赖（依赖变更才失效，源码变更命中此层缓存）
COPY .mvn/ ./.mvn/
COPY mvnw mvnw.cmd ./
COPY pom.xml ./
COPY ainer-dependencies/pom.xml ./ainer-dependencies/
COPY ainer-framework/pom.xml ./ainer-framework/
COPY ainer-framework/ainer-core/pom.xml ./ainer-framework/ainer-core/
COPY ainer-framework/ainer-spring/pom.xml ./ainer-framework/ainer-spring/
COPY ainer-framework/ainer-security/pom.xml ./ainer-framework/ainer-security/
COPY ainer-framework/ainer-starter-web/pom.xml ./ainer-framework/ainer-starter-web/
COPY ainer-framework/ainer-starter-persistence/pom.xml ./ainer-framework/ainer-starter-persistence/
COPY ainer-framework/ainer-starter-security/pom.xml ./ainer-framework/ainer-starter-security/
COPY ainer-framework/ainer-test-support/pom.xml ./ainer-framework/ainer-test-support/
COPY ainer-module-identity/pom.xml ./ainer-module-identity/
COPY ainer-module-workspace/pom.xml ./ainer-module-workspace/
COPY ainer-module-ai-runtime/pom.xml ./ainer-module-ai-runtime/
COPY ainer-module-authorization/pom.xml ./ainer-module-authorization/
COPY ainer-server/pom.xml ./ainer-server/
COPY ainer-authorization-server/pom.xml ./ainer-authorization-server/
COPY ainer-offstate-app/pom.xml ./ainer-offstate-app/
COPY ainer-initializer/pom.xml ./ainer-initializer/
COPY ainer-initializer-cli/pom.xml ./ainer-initializer-cli/

RUN chmod +x ./mvnw \
    && ./mvnw --batch-mode --no-transfer-progress -Denforcer.skip=true dependency:go-offline -DskipTests \
    || echo "[dockerfile] go-offline 部分失败（某些插件未声明也无妨），继续构建"

# 复制全部源码
COPY ainer-framework/ ./ainer-framework/
COPY ainer-module-identity/ ./ainer-module-identity/
COPY ainer-module-workspace/ ./ainer-module-workspace/
COPY ainer-module-ai-runtime/ ./ainer-module-ai-runtime/
COPY ainer-module-authorization/ ./ainer-module-authorization/
COPY ainer-server/ ./ainer-server/
COPY ainer-authorization-server/ ./ainer-authorization-server/
COPY ainer-offstate-app/ ./ainer-offstate-app/
COPY ainer-initializer/ ./ainer-initializer/
COPY ainer-initializer-cli/ ./ainer-initializer-cli/

# 使用 Maven Wrapper 构建（跳过测试，仅打包目标模块及其依赖）
# --batch-mode 禁用交互式进度条，--no-transfer-progress 减少日志噪音
# 注意：-pl ${AINER_MODULE} -am 的产物位于各模块自己的 target/ 目录，而非仓库根 target/
RUN ./mvnw --batch-mode --no-transfer-progress \
       -Denforcer.skip=true \
       -DskipTests \
       -pl "${AINER_MODULE}" -am \
       clean package

# 提取构建产物（产物在 ${AINER_MODULE}/target/，非根 target/）
RUN module_version="$(./mvnw --batch-mode --no-transfer-progress \
       -Denforcer.skip=true \
       -q help:evaluate -Dexpression=project.version -DforceStdout)" \
    && cp "${AINER_MODULE}/target/${AINER_MODULE}-${module_version}.jar" /app.jar \
    && echo "Packaged: ${AINER_MODULE}-${module_version}.jar"

# ---- 运行阶段：JRE 25（更小镜像）----
FROM eclipse-temurin:25-jre-noble

WORKDIR /app

# 非 root 用户运行
RUN groupadd --system ainer && useradd --system --gid ainer --home-dir /app ainer

COPY --from=builder /app.jar /app/app.jar

USER ainer

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
