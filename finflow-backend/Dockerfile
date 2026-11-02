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
# M08 / M11 / M16 / M17 同源多进程：一次 package 产出五个可执行 jar（主 jar、渠道、风控、通知、批处理）
RUN mvn -B -ntp -DskipTests package \
    && cp target/*-SNAPSHOT.jar /build/app.jar \
    && cp target/*-channel.jar /build/channel.jar \
    && cp target/*-risk.jar /build/risk.jar \
    && cp target/*-notify.jar /build/notify.jar \
    && cp target/*-batch.jar /build/batch.jar

# ---------- 运行时（backend） ----------
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/eclipse-temurin:25-jre AS runtime

WORKDIR /app
COPY --from=build /build/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ---------- 运行时（渠道服务，M08；compose 用 target: channel-runtime 构建） ----------
FROM runtime AS channel-runtime

COPY --from=build /build/channel.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=channel

EXPOSE 8092
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ---------- 运行时（风控服务，M11；compose 用 target: risk-runtime 构建） ----------
FROM runtime AS risk-runtime

COPY --from=build /build/risk.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=risk

EXPOSE 8093
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ---------- 运行时（通知服务，M16；compose 用 target: notify-runtime 构建） ----------
FROM runtime AS notify-runtime

COPY --from=build /build/notify.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=notify

EXPOSE 8094
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ---------- 运行时（批处理服务，M17；compose 用 target: batch-runtime 构建） ----------
FROM runtime AS batch-runtime

COPY --from=build /build/batch.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=batch

EXPOSE 8095
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
