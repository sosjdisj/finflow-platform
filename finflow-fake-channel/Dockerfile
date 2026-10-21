# FinFlow 假渠道模拟器镜像（S41）：与后端同构的多阶段构建
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/maven:3.9-eclipse-temurin-21 AS maven

FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/eclipse-temurin:25-jdk AS build

COPY --from=maven /usr/share/maven /opt/maven
ENV PATH=/opt/maven/bin:${PATH}

WORKDIR /build
COPY docker/maven-settings.xml /root/.m2/settings.xml

COPY finflow-fake-channel/pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY finflow-fake-channel/src ./src
RUN mvn -B -ntp -DskipTests package

# ---------- 运行时 ----------
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
