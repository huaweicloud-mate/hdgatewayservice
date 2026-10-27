# hdgatewayservice 蓝区网关镜像
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /opt/cloud/logs/hdgatewayservice
COPY --from=build /build/target/hdgatewayservice-0.0.1.jar app.jar
ENV PORT=3003 \
    LOG_HOME=/opt/cloud/logs/hdgatewayservice
EXPOSE 3003
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
