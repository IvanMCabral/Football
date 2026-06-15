# Setup - MANAGER (football manager)

## Environment Variables (V24D12-D)

El proyecto requiere 18 env vars. Ver `.env.example` para la lista completa con valores dummy.

### Categorias

- **JWT (3, criticas):** `JWT_SECRET`, `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION`.
- **PostgreSQL (3, 2 criticas):** `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- **Redis (3, 1 critica):** `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD`.
- **Simulation (9, no criticas):** `APP_SIMULATION_*` (9 feature flags con default false).

### Setup local (dev)

1. Copiar `.env.example` a `.env`: `cp .env.example .env`.
2. Completar `.env` con tus valores locales (DB local, Redis local, JWT secret generado con `openssl rand -base64 64`).
3. Arrancar el stack: `mvn spring-boot:run -Dspring-boot.run.profiles=local`.
4. Verificar health: `curl http://localhost:8080/api/v1/health` -> 200.

### Setup produccion

NO se commitean credenciales reales. Las env vars se setean en:
- Docker: `docker run -e KEY=VALUE ...`
- K8s: `envFrom: secretRef: { name: manager-secrets }`
- AWS Secrets Manager / Vault / etc.

### Rotacion de credenciales (V24D12-D)

Las credenciales fueron rotadas el 2026-06-15 (V24D12-D) despues de обнаружения que el `.env` estaba trackeado en el repo sin `.gitignore`. Procedimiento de rotacion:

1. PostgreSQL: `ALTER USER manager_user WITH PASSWORD "NUEVO_PASS";` (RDS console o `psql`).
2. Redis: `redis-cli CONFIG SET requirepass "NUEVO_PASS";` + `CONFIG GET requirepass`.
3. JWT: `openssl rand -base64 64` -> nuevo secret.
4. Setear las 3 env vars nuevas en infra (Docker, K8s, AWS Secrets Manager).
5. Reiniciar stack.
6. Smoke: login + endpoint autenticado para validar.

Ver `workspace/rotar-credenciales.md` para el script completo.
