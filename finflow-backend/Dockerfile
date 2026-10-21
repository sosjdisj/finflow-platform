# FinFlow 后端镜像（S41）：多阶段构建，产物只保留 JRE + 可执行 jar
# 基础镜像走华为云同步源（docker.io 国内直连过慢）；Maven 由官方 maven 镜像拷入，避免 apt/curl 下载

# ---------- 构建 ----------
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/maven:3.9-eclipse-temurin-21 AS maven

FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/eclipse-temurin:25-jdk AS build

COPY --from=maven /usr/share/maven /opt/maven
ENV PATH=/opt/maven/bin:${PATH}

WORKDIR /build

# 构建期走阿里云 Maven 镜像
COPY docker/maven-settings.xml /root/.m2/settings.xml

# 先只拷 pom，利用镜像层缓存依赖下载
COPY finflow-backend/pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY finflow-backend/src ./src
RUN mvn -B -ntp -DskipTests package

# ---------- 运行时 ----------
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
