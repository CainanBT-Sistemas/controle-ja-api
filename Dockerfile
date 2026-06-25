FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system controleja && useradd --system --gid controleja controleja

COPY --from=build /workspace/target/*.war app.war

ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx448m -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=64m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -XX:ActiveProcessorCount=2"

USER controleja
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.war"]
