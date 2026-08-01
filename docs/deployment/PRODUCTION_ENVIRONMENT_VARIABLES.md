# MANAGER - Production Environment Variables

Fecha: 2026-07-31

Objetivo: definir las variables requeridas para arrancar el backend en `prod` sin defaults inseguros.

## Regla de arranque

Con `SPRING_PROFILES_ACTIVE=prod`, el backend debe fallar al iniciar si falta una variable crítica o si usa un valor inseguro conocido.

El validador productivo bloquea valores vacíos y valores inseguros como `postgres`, `admin`, `password`, `secret`, `change-me` o el viejo secret JWT por default.

## Variables obligatorias

| Variable | Uso | Ejemplo seguro |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil Spring | `prod` |
| `DB_HOST` | Host PostgreSQL | `db.provider.example` |
| `DB_PORT` | Puerto PostgreSQL | `5432` |
| `DB_NAME` | Base PostgreSQL | `football_manager` |
| `DB_USER` | Usuario PostgreSQL | `manager_app` |
| `DB_PASSWORD` | Password PostgreSQL | secreto generado por proveedor |
| `REDIS_HOST` | Host Redis | `redis.provider.example` |
| `REDIS_PORT` | Puerto Redis | `6379` |
| `REDIS_PASSWORD` | Password/token Redis | secreto generado por proveedor |
| `JWT_SECRET` | Firma JWT HS512 | mínimo 64 bytes aleatorios/base64 largo |
| `APP_CORS_ALLOWED_ORIGINS` | Allowlist CORS | `https://beta.example.com,https://app.example.com` |

## Variables recomendadas

| Variable | Uso | Default |
|---|---|---|
| `PORT` | Puerto cloud compatible | cae a `SERVER_PORT` y luego `8080` |
| `SERVER_PORT` | Puerto local/PaaS clásico | `8080` |
| `REDIS_SSL` | TLS Redis | `true` en `prod`, `false` base/local |
| `JWT_EXPIRATION` | TTL access token ms | `86400000` |
| `JWT_REFRESH_EXPIRATION` | TTL refresh token ms | `604800000` |

## CORS

`APP_CORS_ALLOWED_ORIGINS` debe contener únicamente origins exactos. No se acepta wildcard.

Ejemplo:

```text
APP_CORS_ALLOWED_ORIGINS=https://manager-beta.example.com,https://manager.example.com
```

## Notas operativas

- No cargar secretos en archivos versionados.
- No imprimir secretos en consola.
- Separar secretos de staging y production.
- Rotar cualquier secreto que haya sido usado en local si va a tocar producción.
- En proveedores serverless/managed, configurar estas variables desde secret manager o environment variables del proveedor.

## PB1.1 hardening requirements

Production startup is fail-closed for the following values:

- `JWT_SECRET` must be present, non-blank, not a known insecure value, and at least 64 UTF-8 bytes for HS512.
- `JWT_EXPIRATION` and `JWT_REFRESH_EXPIRATION` must be positive and within the accepted safety window.
- `APP_CORS_ALLOWED_ORIGINS` must contain explicit absolute `http://` or `https://` origins only; wildcard values, partial wildcards, `null`, empty origins, paths, query strings, and fragments are rejected.
- `REDIS_PASSWORD` is required in production.
- `REDIS_USERNAME` is supported for managed Redis ACLs when the provider requires it.
- Redis SSL is controlled by `spring.data.redis.ssl.enabled` / `REDIS_SSL_ENABLED` depending on deployment property binding.
- Rate limiting defaults are enabled in production through `APP_RATE_LIMIT_ENABLED`, `APP_AUTH_RATE_LIMIT_MAX_REQUESTS`, and `APP_AUTH_RATE_LIMIT_WINDOW`.

Never version real secret values. Load secrets through the deployment provider or a local `.env` outside Git.

## PB1.2.1 container runtime additions

The production container artifact also relies on:

| Variable | Uso | Default / regla |
|---|---|---|
| `SERVER_ADDRESS` | Binding de red | `0.0.0.0` en contenedor |
| `JAVA_TOOL_OPTIONS` | Opciones JVM base | memory percentage, UTF-8, UTC, OOM exit |
| `JAVA_OPTS` | Opciones Java adicionales | opcional |
| `SHUTDOWN_TIMEOUT` | Timeout graceful shutdown | `30s` |

For a managed Redis provider, set `REDIS_SSL=true` unless the provider explicitly terminates TLS elsewhere. If ACLs are enabled, set `REDIS_USERNAME` as well as `REDIS_PASSWORD`.

The image must never receive secrets through committed files. Use provider environment variables or secret manager integration.
