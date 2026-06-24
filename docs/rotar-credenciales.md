# Rotacion de credenciales - MANAGER (football manager)

Ejecutar este procedimiento cuando se roten credenciales en infra o como parte del fix de seguridad V24D12-D.

Pasos: 1) generar nuevos valores, 2) rotar en infra, 3) setear env vars nuevas, 4) reiniciar stack, 5) smoke.

## PostgreSQL (DB pass)

Ejecutar en psql contra la DB (RDS console, Docker exec, o local).

    ALTER USER manager_user WITH PASSWORD 'NUEVO_PASS_AQUI';

## Redis (pass)

Ejecutar en redis-cli contra el server.

    redis-cli> CONFIG SET requirepass "NUEVO_PASS_AQUI"
    redis-cli> CONFIG GET requirepass

## JWT secret

Ejecutar en cualquier shell con openssl instalado.

    openssl rand -base64 64

## Despues de rotar

1. Setear las 3 env vars nuevas (DB_PASSWORD, REDIS_PASSWORD, JWT_SECRET) en donde corre el backend:
   - Docker: `docker run -e KEY=VALUE ...`
   - systemd: `EnvironmentFile=/etc/manager/secrets.env`
   - K8s: `envFrom: secretRef: { name: manager-secrets }`
   - AWS Secrets Manager / Vault / etc.
2. Reiniciar el stack.
3. Smoke test: login + endpoint autenticado para validar.
