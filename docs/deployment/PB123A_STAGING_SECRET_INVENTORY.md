# PB1.2.3A staging secret inventory

No secret values are stored in Git or this document.

| Name | Owner | Storage | Rotation |
| --- | --- | --- | --- |
| `DB_PASSWORD` | staging owner | Render secret variable | before public beta and after exposure |
| `REDIS_PASSWORD` | staging owner | Render secret variable | before public beta and after exposure |
| `JWT_SECRET` | staging owner | Render secret variable | generated >=64 UTF-8 bytes; rotate before beta |
| `APP_CORS_ALLOWED_ORIGINS` | staging owner | Render environment variable | update after Firebase origin is known |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | staging owner | Render environment variable | when Neon project changes |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME` | staging owner | Render environment variable | when Upstash database changes |
| `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION` | staging owner | Render environment variable | reviewed with token policy |

Never commit `.env`, deploy hooks, Firebase tokens, Authorization headers or provider connection URLs containing credentials.
