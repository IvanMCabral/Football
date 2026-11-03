#!/bin/sh
set -eu

# Build-time only. Run in the final JRE, from the final application directory.
# No production configuration, credentials or reachable data services are used.
# Docker additionally runs this step with --network=none.
refresh_context() {
  # Match the default heap on the 512 MiB Free instance. A large builder heap
  # can disable compressed references and make its archive unusable at runtime.
  java -Xms102m -Xmx384m -XX:TieredStopAtLevel=1 "$@" \
    -Dspring.context.exit=onRefresh -jar application.jar \
    --spring.config.location=classpath:/application.yaml \
    --spring.profiles.active=cds-training \
    --spring.flyway.enabled=false \
    --spring.r2dbc.pool.initial-size=0 \
    --spring.data.redis.repositories.enabled=false \
    --DB_HOST=127.0.0.1 --DB_PORT=1 --DB_NAME=cds --DB_USER=cds \
    --DB_PASSWORD=cds-training-only \
    --REDIS_HOST=127.0.0.1 --REDIS_PORT=1 --REDIS_PASSWORD=cds-training-only \
    --JWT_SECRET=cds-training-only-not-a-production-key-012345678901234567890123456789 \
    --APP_CORS_ALLOWED_ORIGINS=http://127.0.0.1 --PORT=0
}

refresh_context -XX:ArchiveClassesAtExit=application.jsa
test -s application.jsa
# A build must prove it can reuse the application archive, not merely produce a file.
# The deployed JVM uses auto mode so an intentional JAVA_OPTS change can fall back.
refresh_context -Xshare:on -XX:SharedArchiveFile=application.jsa -Xlog:cds=info
