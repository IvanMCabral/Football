# MANAGER - PB1.1 Production Readiness Audit

Fecha: 2026-07-31

Rol: auditoría de arquitectura y despliegue.

Alcance:

- Backend Spring Boot WebFlux.
- Frontend Angular.
- PostgreSQL.
- Redis.
- Filesystem.
- Docker readiness.
- Cloud Run readiness.
- Firebase readiness.
- Neon readiness.
- Upstash readiness.
- Perfiles Spring.
- Variables de entorno.
- Flyway.
- CORS.
- HTTPS.
- Health.
- Logging.
- Backups.
- Restore.
- Shutdown.
- Scheduler.
- SSE.
- LiveSession.
- Secretos.
- Endpoints debug/test harness/admin.
- Performance.
- Memoria JVM.
- Tiempo de arranque.
- Tamaño del JAR.
- Dependencias nativas.
- Puertos.
- CI/CD readiness.

Restricción aplicada: no se modificó código, tests ni documentación existente. Este archivo es el único artefacto nuevo de auditoría.

## Pregunta central

¿Qué impide hoy que este proyecto pueda correr en Internet?

Respuesta corta: el MVP 1 está funcional y probado localmente, pero todavía no está listo para exposición pública porque carece de hardening productivo en seguridad, perfiles, secretos, CORS, endpoints de diagnóstico, estrategia formal de persistencia Redis, backup/restore operacional, artefactos de contenedor, CI/CD productivo y configuración explícita para plataformas serverless/container.

El impedimento no es el gameplay. El impedimento es la preparación operativa y de seguridad.

## Escala de severidad

- P0: bloquea beta pública. No debe exponerse a Internet antes de resolverlo.
- P1: no bloquea una prueba privada muy controlada, pero sí una beta pública razonable.
- P2: mejora importante de operación/mantenibilidad; puede programarse después del primer hardening.

## Evidencia de repositorio

### Stack

- `pom.xml`: Spring Boot 3.2.1, Java 21, WebFlux, R2DBC PostgreSQL, Redis reactive, Spring Security, Flyway.
- `front-ciber/project/package.json`: Angular 21, Karma/Jasmine.
- `front-ciber/project/angular.json`: output `dist/demo`, build development/production configurados.
- `src/main/resources/application.yaml`: configuración central de DB, Flyway, Redis, JWT, Actuator, logging y flags de simulación.
- `src/main/resources/application-local.yml` y `application-local.properties`: contienen valores locales reales y secretos.
- `src/main/resources/application-career-mutations.yml`: perfil adicional para mutaciones de carrera.
- `front-ciber/project/src/app/environments/environment.ts` y `environment.prod.ts`: `apiUrl: '/api/v1'`.
- `front-ciber/project/proxy.conf.json`: proxy local a `http://localhost:8080`.
- No se observaron artefactos productivos como `Dockerfile`, `.dockerignore`, `docker-compose`, `cloudbuild.yaml`, `firebase.json`, `.firebaserc`, `Procfile`, `render.yaml`, `railway.json` ni workflows de CI/CD en la raíz inspeccionada.

## P0 - Bloqueos para correr en Internet

### P0-01 - CORS refleja cualquier Origin

Evidencia:

- `src/main/java/com/footballmanager/infrastructure/security/SecurityConfig.java`
- `addCorsHeaders` toma `Origin` del request y lo devuelve como `Access-Control-Allow-Origin`.
- `Access-Control-Allow-Credentials` queda en `true`.
- Spring CORS está deshabilitado con `.cors(cors -> cors.disable())`.

Impacto:

- Cualquier sitio puede intentar llamadas autenticadas contra la API si el navegador tiene credenciales/tokens disponibles.
- Aunque el auth sea Bearer, esta configuración no es aceptable para Internet.

Estado:

- P0. Bloquea beta pública.

Criterio de aceptación:

- CORS allowlist por ambiente.
- Solo dominios frontend reales.
- Rechazo explícito de origins desconocidos.
- Tests para preflight y requests con origin no permitido.

### P0-02 - Defaults productivos inseguros

Evidencia:

- `src/main/resources/application.yaml`
  - DB default: `localhost`, `postgres`, password `postgres`.
  - Redis default sin password.
  - `spring.security.user.name=admin`, `password=admin`.
  - `jwt.secret` tiene default: `default-secret-key-please-change-in-production...`.
  - `server.error.include-message=always`.
  - `server.error.include-binding-errors=always`.

Impacto:

- Si una variable de entorno falta en producción, el sistema puede arrancar con defaults locales inseguros.
- El JWT podría ser firmable si se usa el default.
- Errores pueden filtrar detalle interno a usuarios.

Estado:

- P0. Bloquea beta pública.

Criterio de aceptación:

- En perfil `prod`, secretos obligatorios sin default.
- Falla de arranque si falta DB/Redis/JWT.
- Error detail reducido en producción.
- Sin usuario `admin/admin` por default.

### P0-03 - Secretos reales versionados en perfiles locales

Evidencia:

- `src/main/resources/application-local.yml`
- `src/main/resources/application-local.properties`
- Ambos contienen passwords DB/Redis/JWT locales reales o rotados.
- `application-local.properties` incluso declara `DO NOT COMMIT`.

Impacto:

- Aunque sean locales, están dentro de recursos main y pueden terminar empaquetados o visibles.
- Riesgo de reutilización accidental en ambientes conectados.
- Mala práctica de cadena de suministro.

Estado:

- P0. Bloquea exposición pública hasta rotar y aislar.

Criterio de aceptación:

- No hay secretos reales en `src/main/resources`.
- Secrets solo por variables/secret manager.
- Rotación de cualquier secreto expuesto.
- Validación automática que falle si aparecen secretos conocidos.

### P0-04 - Endpoints públicos demasiado amplios

Evidencia:

- `SecurityConfig.java`
  - `/api/v1/teams/**` permitAll.
  - `/api/v1/leagues/**` permitAll.
  - `/api/v1/match-engine/**` permitAll.
  - `/api/v1/fixtures/**` permitAll.
- Comentario: “Match-engine streaming remains public; mutating handlers validate users in-code.”

Impacto:

- Para Internet, las rutas de motor y fixtures son superficie crítica.
- La seguridad distribuida “en código” es más difícil de auditar que una política central.
- Riesgo de lectura/mutación no autenticada si algún handler no valida correctamente.

Estado:

- P0 para beta pública.

Criterio de aceptación:

- Todas las rutas mutantes autenticadas por política central.
- Lecturas públicas justificadas una por una.
- Tests de acceso anónimo para cada grupo de endpoints.

### P0-05 - Admin endpoints presentes en runtime productivo sin modelo admin completo

Evidencia:

- `src/main/java/com/footballmanager/adapters/in/web/world/AdminWorldController.java`
- Ruta: `/api/v1/admin/world`.
- Comentarios indican que permite admin seed para cualquier `userId`.
- `SecurityConfig` exige authenticated para admin world, pero la auditoría no verificó un modelo robusto de provisioning/admin lifecycle/audit log productivo.

Impacto:

- Admin endpoints con posibilidad de seed/impersonation funcional son altamente sensibles.
- Para Internet deben estar separados, role-gated, auditados y preferiblemente fuera del frontend público.

Estado:

- P0 hasta confirmar hardening completo o deshabilitar en `prod`.

Criterio de aceptación:

- Admin endpoints deshabilitados por defecto en producción o protegidos por rol fuerte.
- Audit log real.
- No hay admin seed abierto desde clientes públicos.
- Tests de rol.

### P0-06 - Debug/test harness presente en frontend y backend

Evidencia:

- Backend:
  - `src/main/java/com/footballmanager/adapters/in/web/testharness/TestHarnessController.java`
  - `src/main/java/com/footballmanager/adapters/in/web/testharness/TestHarnessLabsController.java`
  - Ambos con `@Profile({"dev", "local", "test"})`.
- Frontend:
  - Ruta pública Angular: `debug/test-harness`.
  - Servicio frontend apunta a `/api/v1/test-harness/career`.

Impacto:

- Backend queda protegido si `prod` no incluye `dev/local/test`, pero el frontend igualmente publicaría una pantalla de laboratorio.
- Exponer una UI de harness en beta pública transmite superficie de ataque y confusión.

Estado:

- P0 para frontend público.
- P1 para backend si el perfil `prod` queda bien asegurado.

Criterio de aceptación:

- No se incluye ruta debug en build productivo público, o está detrás de guard/feature flag/admin.
- Confirmar por smoke que `/api/v1/test-harness/**` da 404 en `prod`.
- Confirmar que `/debug/test-harness` no está navegable en producción.

### P0-07 - Perfil `prod` no está explícitamente definido

Evidencia:

- Se observaron `application.yaml`, `application-local.yml`, `application-local.properties`, `application-career-mutations.yml`, `application-test.yml`.
- No se observó `application-prod.yml`.

Impacto:

- Producción hereda defaults de `application.yaml`.
- No hay separación clara de logging, error handling, CORS, security, flags, DB pool, Redis TLS, Actuator exposure y secrets requeridos.

Estado:

- P0.

Criterio de aceptación:

- Perfil `prod` explícito.
- Arranque validado con `SPRING_PROFILES_ACTIVE=prod`.
- Falla si se intenta arrancar prod con defaults locales.

### P0-08 - Redis parece almacenamiento crítico de carrera, no solo cache

Evidencia:

- `application.yaml`: comentario `Redis (Career Save Game Storage)`.
- Repositorios Redis para career/standing/live detail.
- Documentación y pruebas anteriores del proyecto validan recuperación con Redis autenticado.

Impacto:

- Si Redis se pierde, pueden perderse carreras, live sessions o estado de torneo.
- Upstash/Redis gestionado puede servir, pero debe definirse persistencia, snapshot, restore y límites.

Estado:

- P0 hasta definir estrategia durable.

Criterio de aceptación:

- Clasificación formal de cada clave Redis: cache vs durable.
- Backup/restore para datos durables o migración de durabilidad a PostgreSQL.
- Prueba de recovery desde pérdida de instancia Redis.

### P0-09 - No hay backup/restore productivo automatizado

Evidencia:

- Hay carpeta local `backups`, pero no se observaron workflows, jobs o scripts productivos versionados para backup/restore cloud.
- No hay runbook específico de producción PB1 para restore.

Impacto:

- Sin restore probado, una beta pública puede perder datos de usuarios.

Estado:

- P0.

Criterio de aceptación:

- Backup automático PostgreSQL.
- Restore drill probado.
- Retención definida.
- Redis durable cubierto o descartado como cache.

### P0-10 - No hay artefacto de contenedor ni contrato de runtime cloud

Evidencia:

- No se observó `Dockerfile`, `.dockerignore`, `Procfile`, `cloudbuild.yaml`, `railway.json`, `render.yaml` ni equivalente.
- Backend usa `SERVER_PORT` default 8080, pero plataformas como Cloud Run inyectan `PORT`.

Impacto:

- No hay forma reproducible de correr el backend en Cloud Run/Fly/Render/Railway como producción.
- No están definidos memoria JVM, startup, health, puerto, build cache ni usuario no-root.

Estado:

- P0 para Cloud Run/container production.
- P1 si el deploy elegido fuese buildpack PaaS sin contenedor, pero igual requiere contrato explícito.

Criterio de aceptación:

- Artefacto runtime reproducible.
- Puerto cloud compatible.
- Health/startup definido.
- Variables requeridas documentadas y validadas.

## P1 - Riesgos importantes previos a beta pública

### P1-01 - Health actual insuficiente

Evidencia:

- `src/main/resources/application.yaml` expone Actuator health.
- `src/main/java/com/footballmanager/adapters/in/web/health/HealthController.java` existe.
- El runbook histórico indicaba no usar `/actuator/health` y usar login vacío como proxy.

Impacto:

- Para Cloud Run y monitoring externo se necesita health/readiness confiable.
- Health debe distinguir app viva, DB disponible, Redis disponible y readiness para recibir tráfico.

Estado:

- P1/P0 según plataforma. En Cloud Run puede bloquear configuración robusta de probes.

Criterio de aceptación:

- `/actuator/health` o `/api/v1/health` validado en prod.
- Readiness incluye DB/Redis.
- Liveness no depende de servicios externos pesados.

### P1-02 - Logging demasiado verboso para producción

Evidencia:

- `application.yaml`: `com.footballmanager: DEBUG`.
- Varios logs debug con ids, squads, fixtures, detalles de simulación.
- `server.error.include-message=always`.

Impacto:

- Costos de logs.
- Posible filtrado de datos de usuarios, ids internos y comportamiento.
- Ruido operacional.

Estado:

- P1.

Criterio de aceptación:

- Nivel `INFO`/`WARN` en prod.
- Logs estructurados o al menos con request id.
- Sanitización de tokens/secrets.

### P1-03 - Flyway productivo requiere política de migración

Evidencia:

- `spring.flyway.enabled=true`.
- `baseline-on-migrate=true`.
- Una migración base: `V1__create_manager_schema.sql`.

Impacto:

- `baseline-on-migrate=true` puede ocultar errores en DB no vacía.
- Sin pipeline de backup pre-migration, un deploy puede dejar schema a mitad de camino.

Estado:

- P1.

Criterio de aceptación:

- Política de migraciones forward-only.
- Backup antes de migraciones.
- Validación en staging.
- Separar conexión JDBC Flyway de R2DBC app si el proveedor lo requiere.

### P1-04 - Frontend usa API relativa pero no tiene estrategia hosting/API path definida

Evidencia:

- `environment.prod.ts`: `apiUrl: '/api/v1'`.
- Para hosting estático separado, `/api/v1` apunta al mismo dominio del frontend salvo rewrites/proxy.

Impacto:

- Firebase/Cloudflare Pages/Vercel necesitan rewrite/proxy o API absoluta.
- Sin eso, el frontend puede desplegar bien pero no comunicarse con backend.

Estado:

- P1.

Criterio de aceptación:

- Estrategia explícita:
  - API bajo mismo dominio con rewrite/proxy, o
  - API absoluta `https://api.<dominio>/api/v1`.
- Build por ambiente.

### P1-05 - Firebase readiness incompleta

Evidencia:

- No se observó `firebase.json` ni `.firebaserc`.
- Angular es SPA y requiere rewrite a `index.html`.

Referencia oficial:

- Firebase Hosting soporta hosting productivo para contenido web y SPAs en CDN global.
- Firebase CLI puede generar rewrite de one-page app a `index.html`.

Impacto:

- Deploy estático a Firebase no está listo sin configuración.

Estado:

- P1.

Criterio de aceptación:

- Config SPA rewrite.
- Output directory correcto.
- API path resuelto.

### P1-06 - Cloud Run readiness incompleta

Evidencia:

- No hay `Dockerfile`.
- No hay configuración explícita para `$PORT`.
- No hay JVM memory flags.
- No hay startup/liveness probes definidos.
- No hay decisión de filesystem temporal.

Referencias oficiales:

- Cloud Run requiere que el contenedor escuche en el puerto indicado por `PORT`.
- Cloud Run soporta startup/liveness probes.
- El filesystem escribible local es efímero/in-memory; no debe usarse como persistencia.

Impacto:

- El backend puede compilar, pero no tiene contrato Cloud Run verificable.

Estado:

- P1/P0 si Cloud Run es plataforma elegida.

Criterio de aceptación:

- Contenedor probado localmente.
- Escucha `$PORT`.
- Health probes.
- Sin dependencia de filesystem persistente local.

### P1-07 - Neon readiness requiere pool/SSL/migrations

Evidencia:

- R2DBC URL actual: `r2dbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`.
- Flyway JDBC separado.
- Pool R2DBC max 20.

Referencia oficial:

- Neon ofrece pooling vía PgBouncer y recomienda entender endpoint pooled/direct según uso.

Impacto:

- La app puede conectarse a Neon, pero se deben validar SSL, endpoint directo para Flyway y pool adecuado.

Estado:

- P1.

Criterio de aceptación:

- R2DBC URL Neon SSL validada.
- Flyway JDBC URL directa validada.
- Pool ajustado a límites.

### P1-08 - Upstash readiness requiere TLS y semántica Redis compatible

Evidencia:

- Spring Data Redis reactive con Lettuce.
- Config actual usa host/port/password, sin propiedad SSL explícita observada.

Referencia oficial:

- Upstash Redis tiene TLS habilitado por defecto y no se puede deshabilitar.

Impacto:

- Si Lettuce no usa SSL contra Upstash, conexión fallará.
- Si se usa REST API de Upstash, habría que cambiar cliente, fuera de esta auditoría.

Estado:

- P1.

Criterio de aceptación:

- Config Redis SSL validada.
- Latencia/command volume medidos.
- Durabilidad Redis resuelta si aplica.

### P1-09 - SSE y streams requieren validación con proxies/CDN

Evidencia:

- `MatchEngineController` expone streams de rondas.
- Frontend tiene polling y comentarios de SSE disabled/polling.
- `SecurityConfig` deja `/api/v1/match-engine/**` público.

Impacto:

- SSE puede cortarse por timeouts/proxies/serverless.
- Si se usa Cloud Run, cada conexión consume instancia/concurrency.
- Si se usa CDN/proxy, hay que validar buffering.

Estado:

- P1.

Criterio de aceptación:

- Definir polling vs SSE en beta.
- Auth en streams.
- Timeouts y reconexión probados detrás de proxy real.

### P1-10 - Shutdown/scheduler deben validarse bajo lifecycle cloud

Evidencia:

- `RoundEngine` maneja scheduler propio con shutdown.
- `application.yaml`: Redis lettuce shutdown-timeout `100ms`.

Impacto:

- Plataformas cloud envían señales de terminación.
- Simulaciones en vivo pueden quedar a mitad si no hay persistencia/checkpoint.

Estado:

- P1.

Criterio de aceptación:

- Graceful shutdown probado.
- LiveSession checkpoint/recovery probado bajo SIGTERM/restart.
- Scheduler no deja threads zombie.

### P1-11 - CI/CD readiness insuficiente

Evidencia:

- No se observaron workflows GitHub Actions ni equivalente.
- No hay pipeline productivo de build/test/deploy/rollback.

Impacto:

- Deploy manual aumenta riesgo.
- Sin gates automáticos, un cambio puede romper beta.

Estado:

- P1.

Criterio de aceptación:

- Pipeline backend/frontend.
- Secrets de CI.
- Deploy staging.
- Smoke mínimo.
- Manual gate a producción.

### P1-12 - Dependencias y tamaño runtime no auditados para cloud

Evidencia:

- No se ejecutó build en esta auditoría por restricción de solo auditar.
- JAR size, startup time y memoria JVM no están documentados en el estado actual inspeccionado.
- Recursos `src/main/resources/seed/*.json` suman varios MB y se empaquetan si no se excluyen.

Impacto:

- Puede afectar cold start, memoria y costo.

Estado:

- P1.

Criterio de aceptación:

- Medir JAR.
- Medir startup.
- Definir `JAVA_TOOL_OPTIONS`.
- Revisar datasets legacy empaquetados si no son necesarios en producción.

## P2 - Mejoras operativas y de calidad

### P2-01 - Observabilidad profesional pendiente

Necesario:

- Request id/correlation id.
- Métricas de simulación.
- Métricas de LiveSession.
- Panel de errores 4xx/5xx.
- Alertas por latencia.

### P2-02 - Rate limiting no observado

Impacto:

- Auth, register y endpoints de simulación podrían abusarse.

Estado:

- P2/P1 si se abre registro público sin invitaciones.

### P2-03 - Política de archivos locales

Evidencia:

- Hay logs y backups locales en raíz.
- Cloud Run/Firebase no proveen filesystem persistente local usable para estado.

Estado:

- P2 si solo son artefactos locales ignorados.
- P1 si algún flujo productivo los requiere.

### P2-04 - Actuator metrics expuesto parcialmente

Evidencia:

- `management.endpoints.web.exposure.include=health,info,metrics`.

Impacto:

- `metrics` puede ser útil, pero debe requerir auth o red privada en producción.

Estado:

- P2/P1 según exposición final.

### P2-05 - Rutas frontend legacy/admin/debug visibles

Evidencia:

- Frontend conserva rutas de gestión, creación manual y debug.

Impacto:

- No necesariamente inseguro si backend protege, pero baja la percepción profesional.

Estado:

- P2, excepto debug/test-harness que es P0 para build público.

## Readiness por área

| Área | Estado | Severidad dominante | Comentario |
|---|---|---|---|
| Backend funcional | Parcialmente listo | P1 | Motor y APIs existen, falta hardening prod. |
| Frontend funcional | Parcialmente listo | P1 | Build existe, falta estrategia de API/deploy y ocultar debug. |
| PostgreSQL | Parcialmente listo | P1 | R2DBC/Flyway listo, falta operación cloud/backup/restore. |
| Redis | No listo para Internet | P0 | Es storage crítico y necesita estrategia durable. |
| Filesystem | Parcialmente listo | P2/P1 | No debe depender de archivos locales. |
| Docker | No listo | P0/P1 | No hay artefacto reproducible. |
| Cloud Run | No listo | P1 | Faltan container contract, probes, env, memory. |
| Firebase | No listo | P1 | Falta config hosting/rewrite/API. |
| Neon | Parcialmente listo | P1 | Compatible conceptualmente, falta SSL/pool/Flyway direct URL. |
| Upstash | Parcialmente listo | P1 | Compatible conceptualmente, falta TLS config y durabilidad. |
| Spring profiles | No listo | P0 | Falta prod explícito. |
| Variables entorno | Parcial | P0 | Defaults inseguros y secretos locales. |
| Flyway | Parcial | P1 | Falta política productiva. |
| CORS | No listo | P0 | Refleja origin. |
| HTTPS | Externo/no configurado | P1 | No hay dominio/proxy definido. |
| Health | Parcial | P1 | Existe config/controlador, falta readiness real validado. |
| Logging | No listo | P1 | DEBUG y errores detallados. |
| Backups | No listo | P0 | Sin automatización/restore. |
| Restore | No listo | P0 | Sin drill productivo. |
| Shutdown | Parcial | P1 | Scheduler existe, falta prueba cloud. |
| Scheduler | Parcial | P1 | Debe validarse con lifecycle cloud. |
| SSE | Parcial | P1 | Necesita auth/proxy/timeout. |
| LiveSession | Parcial | P0/P1 | Funcional, falta modelo durable cloud. |
| Secretos | No listo | P0 | Defaults y secretos locales. |
| Debug endpoints | Parcial | P0/P1 | Backend profile-gated; frontend visible. |
| Admin endpoints | No listo | P0 | Deben harden/deshabilitar. |
| Performance | No medido | P1 | Faltan números cloud. |
| Memoria JVM | No definido | P1 | Faltan flags/medición. |
| Startup time | No medido | P1 | Relevante para Cloud Run/scale-to-zero. |
| JAR size | No medido | P1 | Recursos seed pueden inflar artefacto. |
| Dependencias nativas | Bajo riesgo | P2 | Java/Node estándar; no se observaron binarios nativos backend. |
| Puertos | Parcial | P1 | Usa `SERVER_PORT`, pero Cloud Run usa `PORT`. |
| CI/CD | No listo | P1 | No hay workflows observados. |

## Qué está listo

- Arquitectura funcional backend/frontend validada en MVP 1.
- Backend usa WebFlux/R2DBC/Redis reactivo, compatible conceptualmente con cloud.
- PostgreSQL + Flyway ya existen.
- Redis ya está integrado.
- Angular tiene builds development/production configurados.
- Actuator está declarado en configuración.
- Test harness backend está profile-gated para `dev/local/test`.
- Dataset MVP 1 está empaquetado y validado previamente.
- No se observaron dependencias nativas complejas en backend.

## Qué falta

P0:

1. Cerrar CORS.
2. Crear perfil `prod` explícito.
3. Eliminar defaults inseguros y secretos versionados del runtime.
4. Proteger/deshabilitar endpoints admin.
5. Ocultar/eliminar test harness del frontend público.
6. Definir estrategia durable Redis.
7. Automatizar backup y probar restore.
8. Crear artefacto runtime reproducible para cloud/container o plataforma elegida.

P1:

1. Health/readiness formal.
2. Logging productivo.
3. Política Flyway productiva.
4. Estrategia API para frontend estático.
5. Validación Cloud Run/Firebase/Neon/Upstash real.
6. Validación SSE/polling detrás de proxy.
7. Graceful shutdown cloud.
8. CI/CD.
9. Medición de startup, memoria y JAR.

P2:

1. Observabilidad más fina.
2. Rate limiting.
3. Métricas de simulación.
4. Limpieza de rutas legacy visibles.
5. Revisión de recursos legacy empaquetados.

## Qué orden seguir

1. Seguridad mínima P0:
   - CORS;
   - prod profile;
   - secretos;
   - endpoints admin/debug;
   - endpoints públicos.
2. Persistencia y recuperación:
   - Redis durable/cache;
   - backup Postgres;
   - restore drill;
   - recovery LiveSession.
3. Runtime cloud:
   - Docker/buildpack contract;
   - puerto;
   - health;
   - JVM memory;
   - filesystem.
4. Frontend public hosting:
   - API URL/rewrite;
   - ocultar debug;
   - Firebase/Cloudflare/Vercel readiness.
5. CI/CD y release gates:
   - build/test;
   - deploy staging;
   - smoke;
   - rollback.
6. Observabilidad:
   - logs;
   - Sentry;
   - uptime;
   - métricas.

## Riesgos

- Exposición de endpoints mutantes o de motor sin auth central.
- CORS permisivo con credenciales.
- Arranque accidental con secretos/defaults inseguros.
- Pérdida de carreras si Redis falla.
- Deploy sin restore probado.
- Frontend deployado sin API funcional por `apiUrl` relativa.
- Cloud Run fallando por puerto/probe/memoria.
- Upstash fallando por TLS no configurado.
- Neon con problemas de pool/Flyway si se usa endpoint incorrecto.
- Logs DEBUG elevando costo y filtrando datos.

## Estimación

Estimación para dejar PB1 listo para una beta pública pequeña:

- Hardening P0: 3 a 6 días de trabajo efectivo.
- Persistencia/backup/restore: 2 a 4 días.
- Runtime cloud + frontend hosting: 2 a 4 días.
- CI/CD + smoke + rollback: 2 a 4 días.
- Observabilidad mínima: 1 a 2 días.

Total realista:

- 10 a 20 días de trabajo efectivo si no aparecen regresiones.
- 1 semana solo sería viable para una demo privada controlada, no para beta pública responsable.

## Roadmap PB1

### PB1-A - Internet Safety Gate

- Perfil `prod`.
- Secrets obligatorios.
- CORS allowlist.
- Admin/debug/test harness fuera de producción pública.
- Endpoints públicos auditados.

### PB1-B - Data Safety Gate

- PostgreSQL backups automáticos.
- Restore drill.
- Redis durability decision.
- Recovery test después de restart.

### PB1-C - Runtime Gate

- Artefacto container/buildpack.
- Health/readiness.
- Puerto cloud.
- JVM memory.
- Startup time.
- Filesystem stateless.

### PB1-D - Frontend Gate

- Hosting SPA.
- API routing.
- Build productivo sin debug público.
- HTTPS/domain.

### PB1-E - Operations Gate

- CI/CD.
- Staging.
- Rollback.
- Logs.
- Uptime.
- Error tracking.

## Veredicto

REJECTED FOR PUBLIC INTERNET BETA.

El MVP 1 está congelado y funcional, pero hoy no debe exponerse públicamente en Internet. La razón no es falta de features, sino ausencia de controles productivos esenciales: seguridad de CORS/secrets/endpoints, perfil `prod`, persistencia/restore, Redis durable, runtime cloud reproducible y CI/CD.

Puede avanzar a una beta privada cerrada solo si se opera detrás de acceso restringido y sin usuarios externos reales. Para beta pública PB1.1, los P0 deben resolverse primero.

## Production Hardening Remediation

Fecha: 2026-07-31

Se ejecutó PB1.1 Production Security Hardening sobre los P0 accionables sin modificar gameplay, motor, probabilidades, datasets ni reglas deportivas.

Estado posterior:

- Perfil `prod` explícito agregado.
- Defaults inseguros eliminados de la configuración base.
- Arranque `prod` bloqueado si faltan variables críticas o valores seguros.
- CORS centralizado por allowlist configurable.
- Wildcards CORS removidos de controllers.
- Admin, seed, debug y test harness fuera del runtime `prod`.
- Frontend production sin ruta `debug/test-harness`.
- Rutas sensibles ya no quedan públicas por política central.
- Redis clasificado como runtime crítico y documentado.
- Health valida PostgreSQL y Redis.
- Logging productivo reducido.
- Backend validado con 2536 tests, 0 failures, 0 errors, 4 skipped.
- Frontend validado con builds development/production y 1028 tests SUCCESS, 0 failures, 2 skipped.

P0 que pasan a PB1.2 por restricción explícita de esta fase:

- Backup/restore automatizado real, porque PB1.1 prohibió crear CI/CD/cloud/deploy.
- Artefacto Docker/Cloud Run/Firebase, porque PB1.1 prohibió crear Docker/Cloud Run/Firebase.

Nuevo estado:

APPROVED FOR PB1.2 PRODUCTION RUNTIME WORK.

No aprobado todavía para beta pública abierta hasta completar PB1.2 con infraestructura real, backups automáticos, restore drill, CI/CD y smoke contra proveedores.

## Fuentes oficiales consultadas para criterios cloud

- Cloud Run container runtime contract: https://docs.cloud.google.com/run/docs/container-contract
- Cloud Run health checks: https://docs.cloud.google.com/run/docs/configuring/healthchecks
- Cloud Run in-memory volumes/filesystem: https://docs.cloud.google.com/run/docs/configuring/services/in-memory-volume-mounts
- Firebase Hosting: https://firebase.google.com/docs/hosting
- Firebase Hosting quickstart / SPA rewrite: https://firebase.google.com/docs/hosting/quickstart
- Neon connection pooling: https://neon.com/docs/connect/connection-pooling
- Upstash Redis client connection and TLS: https://upstash.com/docs/redis/howto/connect-client
