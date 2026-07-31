# MANAGER - Public Beta v1.1 Deployment Plan

Fecha: 2026-07-31

Objetivo: preparar el MVP 1 congelado para una beta pública en Internet sin agregar gameplay, mercado, economía, scouting ni cambios funcionales.

## 1. Estado técnico observado

- Frontend: Angular en `front-ciber/project`.
- Backend: Spring Boot 3.2.1, WebFlux, Spring Security, R2DBC PostgreSQL, Redis reactivo, Flyway.
- Base de datos: PostgreSQL.
- Estado temporal / live sessions: Redis.
- Build backend: Maven, Java 21.
- Build frontend: Angular CLI.
- Documentación operativa local: `MANAGER_TEAM_RUNBOOK.md`.
- Health actual: el runbook indica que no debe usarse `/actuator/health`; hoy se usa login vacío como proxy de liveness. Para beta pública esto debe reemplazarse por un endpoint explícito de health/readiness antes del deploy real.

## 2. Arquitectura recomendada para beta pública

Recomendación principal de bajo costo y bajo mantenimiento:

```text
Usuario
  |
  | HTTPS
  v
Cloudflare DNS + CDN
  |
  +--> Frontend Angular: Cloudflare Pages
  |
  +--> API: backend Spring Boot en Railway Hobby o Render Starter
          |
          +--> PostgreSQL: Neon Free/Launch o Supabase Free/Pro
          |
          +--> Redis: Upstash Redis
```

### Por qué esta arquitectura

- Mantiene el frontend separado del backend, con CDN y HTTPS gratuitos.
- Evita operar servidores propios durante la beta.
- Permite empezar barato y migrar por componentes.
- PostgreSQL y Redis quedan gestionados por proveedores especializados.
- El backend puede moverse luego a Docker/Kubernetes/VM sin reescribir el producto.

## 3. Alternativas de arquitectura

### Opción A - Recomendada para beta pública simple

| Componente | Proveedor recomendado | Motivo |
|---|---|---|
| Frontend Angular | Cloudflare Pages | CDN, HTTPS, custom domain, plan gratuito generoso. |
| Backend Spring Boot | Railway Hobby | Deploy simple desde Git/Docker, variables, logs, HTTPS. |
| PostgreSQL | Neon o Supabase | Managed Postgres, bajo costo inicial, fácil migración. |
| Redis | Upstash | Redis gestionado con plan gratuito inicial. |
| DNS/HTTPS | Cloudflare | DNS robusto, SSL, proxy, protección básica. |
| Monitoring | UptimeRobot/Better Stack + Sentry | Bajo costo y suficiente para beta. |
| CI/CD | GitHub Actions + deploy automático | Pipeline claro, reproducible, migrable. |

Costo esperado: USD 5-15/mes más dominio.

### Opción B - Costo mínimo, más operación

| Componente | Proveedor recomendado | Motivo |
|---|---|---|
| Frontend Angular | Cloudflare Pages | Gratis. |
| Backend Spring Boot | Oracle Cloud Always Free VM | Puede ser USD 0 si hay capacidad disponible. |
| PostgreSQL | Neon Free o Postgres autogestionado en VM | Neon reduce operación; self-host reduce costo pero aumenta riesgo. |
| Redis | Upstash Free o Redis en VM | Upstash reduce operación; Redis en VM requiere backups propios. |
| DNS/HTTPS | Cloudflare + reverse proxy | Gratis, pero hay que operar certificados/proxy si se usa VM. |

Costo esperado: USD 0-5/mes más dominio, con más mantenimiento.

### Opción C - Todo en plataforma PaaS

| Componente | Proveedor recomendado | Motivo |
|---|---|---|
| Frontend | Render Static Site, Vercel o Cloudflare Pages | Deploy simple. |
| Backend | Render/Railway | Menos piezas. |
| PostgreSQL | Mismo proveedor si conviene | Menos cuentas. |
| Redis | Mismo proveedor o Upstash | Menos configuración. |

Costo esperado: USD 10-30/mes. Más simple, menos óptimo en costo.

## 4. Decisión por componente

### Frontend Angular

Primera opción: Cloudflare Pages.

Configuración esperada:

- Root del proyecto frontend: `front-ciber/project`.
- Build command: `npm ci && npm run build`.
- Output directory: verificar en CI; normalmente `dist/<app-name>` para Angular actual.
- Variables públicas: URL base del backend, ambiente, versión.
- Dominio sugerido: `https://manager.<dominio>` o `https://app.<dominio>`.

Ventajas:

- HTTPS automático.
- CDN global.
- Custom domains.
- Deploy previews.
- Rollback visual desde deployments anteriores.
- Plan gratuito fuerte para sitios estáticos.

Desventajas:

- Solo sirve frontend estático; el backend debe vivir en otro lado.
- Variables del frontend quedan embebidas en build si se usan como config pública.

Migración futura:

- Muy fácil. El build Angular es estático; se puede mover a Vercel, Netlify, S3, Nginx o cualquier CDN.

### Backend Spring Boot WebFlux

Primera opción de bajo mantenimiento: Railway Hobby.

Alternativa fuerte: Render Starter.

Alternativa costo cero con más operación: Oracle Cloud Always Free VM.

Configuración esperada:

- Java 21.
- Build Maven.
- Dockerfile recomendado antes del deploy real, aunque no se crea en esta tarea.
- Variables de entorno:
  - `SPRING_PROFILES_ACTIVE=prod`
  - `DB_HOST`
  - `DB_PORT`
  - `DB_NAME`
  - `DB_USER`
  - `DB_PASSWORD`
  - `REDIS_HOST`
  - `REDIS_PORT`
  - `REDIS_PASSWORD`
  - `JWT_SECRET`
  - `FRONTEND_ORIGIN`
- Puerto: usar `$PORT` del proveedor.
- Health: agregar endpoint público de liveness/readiness antes de producción.

Ventajas Railway/Render:

- Deploy desde Git.
- HTTPS gestionado.
- Logs disponibles.
- Variables seguras.
- Rollback razonable por deployment anterior.
- Menos mantenimiento que una VM.

Desventajas:

- Free tiers no son adecuados para producción real.
- Railway puede generar costo por uso.
- Render Free duerme servicios y su Postgres Free expira.
- En WebFlux hay que cuidar pools, timeouts y cold starts.

Migración futura:

- Media/fácil si se usa Docker y variables estándar.
- Evitar acoplarse a APIs específicas del proveedor.

### PostgreSQL

Primera opción: Neon.

Alternativa: Supabase.

Supabase conviene si en MVP2 se quiere tablero, auth, storage o funciones alrededor de Postgres. Neon conviene si se busca Postgres serverless, branching y simpleza.

Requisitos:

- Flyway debe ejecutar migraciones de forma controlada.
- Backups antes de deploys que cambien schema.
- Pooling compatible con R2DBC.
- SSL obligatorio.
- Conexiones máximas limitadas para beta.

Advertencia:

- Oracle Autonomous Database Always Free no es PostgreSQL; no sirve como drop-in sin cambiar tecnología.
- Postgres autogestionado en Oracle VM baja costos, pero sube mucho la responsabilidad de backup, upgrades, seguridad y restore.

### Redis

Primera opción: Upstash Redis.

Uso esperado:

- Live sessions.
- Estados temporales del partido.
- Cache operacional.

Riesgo principal:

- Si Redis guarda datos necesarios para recuperar carreras o partidos vivos, debe tratarse como almacenamiento durable, no como cache.
- Para beta pública, hay que definir una de estas dos rutas antes de abrir usuarios reales:
  1. usar Redis gestionado con persistencia y estrategia de backup/restore;
  2. mover estado durable a PostgreSQL y dejar Redis solo como cache/session store.

Para MVP 1 congelado no se cambia código, pero el release gate de beta debe contener esta decisión.

### HTTPS y dominio

Recomendado:

- Comprar dominio propio.
- Gestionar DNS en Cloudflare.
- Frontend en `app.<dominio>` o raíz.
- API en `api.<dominio>`.
- HTTPS automático de Cloudflare Pages y proveedor backend.
- CORS del backend limitado al dominio real del frontend.

No recomendado:

- Exponer la API con CORS abierto.
- Usar URLs temporales del proveedor como URL pública final.
- Usar Cloudflare Tunnel desde una PC local para beta pública estable.

### Backups

PostgreSQL:

- Backup diario `pg_dump -Fc`.
- Backup antes de cada deploy con migraciones.
- Retención mínima beta:
  - diarios: 7 días;
  - semanales: 4 semanas;
  - mensuales: 3 meses.
- Guardar dumps cifrados fuera del proveedor principal.
- Destino barato: Cloudflare R2, Backblaze B2, S3 compatible o almacenamiento privado cifrado.

Redis:

- Si se usa solo como cache: no requiere restore funcional.
- Si contiene estado de carreras/live sessions: requiere snapshot/export o rediseño para persistir estado durable en PostgreSQL.

### Restore

Procedimiento mínimo:

1. Crear base temporal.
2. Restaurar último dump.
3. Levantar backend staging contra esa base.
4. Ejecutar smoke de login, carrera, plantel, fixture, partido detallado y standings.
5. Documentar duración del restore y checksum del backup.

Frecuencia:

- Un restore drill antes de abrir beta.
- Luego mensual durante la beta.

### Monitoring

Mínimo viable:

- Uptime externo cada 1-5 minutos:
  - frontend;
  - backend health;
  - login/auth superficial;
  - endpoint de partido o fixture si hay endpoint seguro para smoke.
- Alertas por email/Telegram/Discord.
- Sentry para errores frontend y backend.
- Métricas de proveedor:
  - CPU;
  - memoria;
  - reinicios;
  - latencia;
  - errores 5xx;
  - conexiones DB;
  - comandos Redis.

Antes de beta pública conviene agregar:

- `/actuator/health` o endpoint equivalente.
- Correlation/request id en logs.
- Métrica de simulaciones por minuto y errores por simulación.

### Logs

Recomendado:

- Logs estructurados JSON en backend.
- Sin secretos ni tokens en logs.
- Retención mínima 7-14 días.
- Nivel `INFO` para producción, `DEBUG` solo temporalmente.
- Centralizar en proveedor o Better Stack/Logtail si el volumen crece.

### Secrets

Reglas:

- No subir `.env`.
- No imprimir secretos en CI.
- Separar secretos de staging y producción.
- Rotar `JWT_SECRET` antes de beta si fue usado localmente.
- Usar variables del proveedor.
- Accesos mínimos por cuenta.

Secrets mínimos:

- DB URL/host/user/password.
- Redis URL/password.
- JWT secret.
- CORS origin.
- Credenciales de backup.
- Tokens de deploy.
- Sentry DSN.

### CI/CD

Pipeline recomendado:

1. Pull request:
   - backend compile;
   - backend tests;
   - frontend development build;
   - frontend production build;
   - frontend tests;
   - lint/format si existe;
   - `git diff --check`.
2. Merge a main:
   - build imagen backend;
   - correr migraciones en staging;
   - deploy staging;
   - smoke staging;
   - deploy frontend preview/production;
   - deploy backend production manual-gated al principio.
3. Release tag:
   - backup pre-release;
   - deploy production;
   - smoke production;
   - registrar evidencia.

Para beta pública conviene empezar con deploy automático a staging y deploy manual aprobado a producción. Después de estabilizar, se puede automatizar producción.

### Rollback

Frontend:

- Cloudflare Pages permite volver a un deployment anterior.

Backend:

- Mantener imágenes/versiones inmutables.
- Rollback al deployment anterior desde Railway/Render/Fly.
- Variables versionadas por ambiente.

Base de datos:

- Flyway migrations deben ser forward-only.
- Antes de migraciones: backup.
- Si una migración rompe producción, rollback de app no alcanza si el schema cambió.
- Para beta: migraciones pequeñas, compatibles hacia atrás cuando sea posible.

Redis:

- Si guarda estado durable, un rollback de backend puede no ser suficiente si cambió formato de datos.
- Versionar claves o mantener compatibilidad durante una ventana.

### Escalabilidad inicial

Beta inicial:

- 1 instancia backend.
- PostgreSQL managed.
- Redis managed.
- Frontend CDN.
- Pool DB pequeño.
- Timeouts claros.
- Límite de usuarios beta.

Escalado:

- Escalar backend horizontalmente solo si LiveSession ya está correctamente externalizada o particionada.
- Redis compartido permite múltiples instancias si no hay estado en memoria local.
- Para simulaciones pesadas, considerar worker/job queue en MVP2, no en esta etapa.

## 5. Comparación de proveedores

| Proveedor | Mejor uso en MANAGER | Ventajas | Desventajas | Límites gratis / bajo costo | Mantenimiento | Migración futura |
|---|---|---|---|---|---|---|
| Railway | Backend Spring Boot; opcional DB/Redis | Muy simple, Git deploy, HTTPS, env vars, logs | Free muy limitado; Hobby cuesta; costo por uso | Free con crédito mensual muy chico; Hobby desde costo bajo | Bajo | Media si se usa Docker |
| Render | Backend o static site | Fácil, estable, HTTPS, logs | Free web duerme; Free Postgres expira | Free no recomendado para producción; Postgres Free temporal | Bajo | Fácil |
| Fly.io | Backend container cerca del usuario | Buen runtime global, Docker, redes privadas | Ya no es free continuo; más operación | Trial/créditos, costo por uso | Medio | Media |
| Oracle Cloud Free | VM para backend/Redis/self-host | Puede costar USD 0, recursos Always Free | Más DevOps, backups y seguridad propios; disponibilidad variable | Always Free compute en región home | Alto | Media |
| Supabase | PostgreSQL gestionado | Dashboard, SQL, APIs, auth/storage futuros | Free limitado y pausa por inactividad | Free con 500 MB DB y límites de egress/storage | Bajo | Fácil: es Postgres |
| Neon | PostgreSQL gestionado | Serverless Postgres, branching, bajo costo inicial | Cold starts/limits; sin Redis | Free con allowances de compute/storage | Bajo | Fácil: es Postgres |
| Upstash | Redis gestionado | Serverless, simple, free inicial | Cuota de comandos; cuidado si Redis es durable | Free con 256 MB y 500K comandos/mes | Bajo | Media/fácil |
| Cloudflare Pages | Frontend Angular | CDN, HTTPS, custom domains, free fuerte | No corre backend Java | Free con builds mensuales y sitios estáticos | Muy bajo | Muy fácil |
| Cloudflare Tunnel | Exponer VM/servicio privado | Útil con Oracle/self-host, sin abrir puertos directos | No reemplaza hosting; dependencia operacional | Zero Trust/Tunnel con plan gratuito útil | Medio | Media |
| Vercel | Frontend estático/preview | DX excelente, HTTPS, previews | Hobby puede tener restricciones de uso; Angular menos nativo que Next | Hobby gratis para proyectos chicos/personales | Muy bajo | Muy fácil |

## 6. Recomendación final para beta v1.1

### Camino recomendado

1. Cloudflare DNS + dominio propio.
2. Cloudflare Pages para Angular.
3. Railway Hobby para backend Spring Boot.
4. Neon Free/Launch para PostgreSQL.
5. Upstash Redis para beta inicial.
6. GitHub Actions para CI.
7. Sentry + UptimeRobot/Better Stack para monitoring.
8. Backups diarios PostgreSQL a almacenamiento externo.
9. Deploy automático a staging y manual-gated a producción.

### Gate técnico antes de abrir usuarios reales

- Endpoint health/readiness formal.
- CORS cerrado al dominio real.
- Secrets rotados.
- Backup y restore drill probado.
- Decisión explícita sobre Redis durable vs cache.
- Límite de usuarios beta.
- Smoke de carrera completa en producción.
- Política de rollback documentada.

## 7. Fuentes consultadas

- Railway pricing: https://railway.com/pricing
- Railway free trial docs: https://docs.railway.com/reference/pricing/free-trial
- Railway plans docs: https://docs.railway.com/reference/pricing/plans
- Render free tier docs: https://render.com/docs/free
- Render PostgreSQL docs: https://render.com/docs/postgresql-creating-connecting
- Fly.io pricing/docs: https://fly.io/docs/about/pricing/
- Oracle Cloud Free Tier: https://www.oracle.com/cloud/free/
- Oracle Always Free resources: https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier.htm
- Supabase pricing: https://supabase.com/pricing
- Neon pricing: https://neon.com/pricing
- Neon plans docs: https://neon.com/docs/introduction/plans
- Upstash Redis pricing: https://upstash.com/pricing/redis
- Cloudflare Pages: https://pages.cloudflare.com/
- Cloudflare Workers and Pages pricing: https://developers.cloudflare.com/workers/platform/pricing/
- Vercel plans: https://vercel.com/docs/plans
