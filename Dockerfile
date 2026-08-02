# syntax=docker/dockerfile:1.7

# Maven 3.9.11 / Temurin 21, linux/amd64 manifest digest verified from Docker Hub.
FROM maven:3.9.11-eclipse-temurin-21@sha256:463a1849665463254b2dd56e3a5b316f1596bc93d0571065c06ea05bb48ab8f4 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

# Temurin 21.0.11_10 JRE Alpine 3.23, linux/amd64 manifest digest verified from Docker Hub.
FROM eclipse-temurin:21.0.11_10-jre-alpine-3.23@sha256:426401268a42785be73823f6115ee0e721bdb59c12c779947b83fcead1a66645

ENV TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_ADDRESS=0.0.0.0 \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=20 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom"

RUN apk add --no-cache curl ca-certificates tzdata shadow findutils \
    && apk upgrade --no-cache \
    && groupadd --system manager \
    && useradd --system --gid manager --home-dir /app --create-home manager
WORKDIR /app
COPY --from=build --chown=manager:manager /workspace/target/*.jar /app/app.jar

USER manager
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl --fail --silent --show-error --max-time 3 "http://127.0.0.1:${PORT}/api/v1/health/liveness" >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar /app/app.jar \"$@\"", "--"]
