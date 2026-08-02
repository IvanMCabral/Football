# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21.0.8_9-jre-jammy

ENV TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_ADDRESS=0.0.0.0 \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=20 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system manager \
    && useradd --system --gid manager --home-dir /app --create-home manager
WORKDIR /app
COPY --from=build --chown=manager:manager /workspace/target/*.jar /app/app.jar

USER manager
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl --fail --silent --show-error --max-time 3 "http://127.0.0.1:${PORT}/api/v1/health/liveness" >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar /app/app.jar \"$@\"", "--"]
