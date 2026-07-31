# MANAGER — Auditoría independiente PB1.1 Production Hardening

Fecha: 2026-07-31  
Alcance: configuración productiva, secretos, CORS, endpoints sensibles, frontend production, Redis, health, errores, logging, headers, autenticación y preparación de PB1.2.  
Modo: auditoría técnica independiente de solo lectura. No se modificaron código, tests existentes, configuración, DB, Redis ni documentación existente.

## 1. Veredicto

`PB1.1 HARDENING REJECTED`

El hardening cerró varias medidas importantes, pero no alcanza para aprobar una exposición productiva. Encontré bloqueantes P0 que no aparecen cubiertos por los tests ni por la documentación de cierre:

- `/api/v1/editor` permanece registrado en `prod` y contiene un UUID fijo en `EditorController` para resolver el usuario, además de operaciones de creación y modificación de world data.
- `/api/v1/world/leagues/{leagueId}/add-team` y `/remove-team/{teamId}` permanecen registrados en `prod` y aceptan `userId` desde request sin comparar contra el usuario autenticado.
- Hay credenciales de DB/Redis versionadas como defaults en recursos de test, tests, runbooks y scripts.

También quedan P1 de validación de secretos, exposición de errores internos, rate limiting, Redis durable y graceful shutdown.

## 2. Commits auditados

### Root/backend

- `975a8a68` — Harden production configuration.
- `9d55adcf` — Secure production runtime.
- `0b109a5a` — Close PB1.1 production hardening.
- HEAD auditado: `0b109a5a`.

### Frontend

- `8a87b83` — Secure production runtime.
- HEAD auditado: `8a87b83`.

## 3. Git y alcance

- Root/backend: limpio antes de crear este informe.
- Frontend: limpio antes de crear este informe.
- El rango `ccd8a9eb..0b109a5a` contiene configuración productiva, guardas de endpoints, CORS, health, tests y documentación. No se observaron cambios de gameplay, simulación, probabilidades, datasets o reglas deportivas en ese rango.
- `git diff --check ccd8a9eb..0b109a5a`: limpio.
- `git diff --check` del frontend: limpio.
- El test `ProductionEndpointProfileTest` cubre admin, seed, debug y test harness, pero no cubre `EditorController` ni `LeagueTeamCommandController`.

## 4. Perfil `prod`

Hay un `application-prod.yml` explícito y los controllers de admin, seed y test harness están protegidos mediante `@Profile`:

- `CareerAdminController`: `@Profile("!prod")`.
- `AdminWorldController`: `@Profile("!prod")`.
- `WorldSeedController` y `LaLigaSeedController`: `@Profile("!prod")`.
- `CareerDebugController`: solo `dev`.
- `TestHarnessController` y `TestHarnessLabsController`: `dev`, `local`, `test`.

El intento real de arranque con `prod` sin variables no arrancó y falló antes del contexto completo por placeholders obligatorios de DB. Esto es fail-closed, pero no llegó a ejecutar el mensaje específico de `ProductionStartupValidation`.

Los tests de `ProductionStartupValidation` verifican variables ausentes, valores inseguros y perfil no productivo usando `MockEnvironment`. No prueban una instancia completa de Spring con la matriz real de variables, DB, Redis y mappings.

### Hallazgo P0

`EditorController` no tiene `@Profile` y se carga en `prod`. Expone:

- `/api/v1/editor/custom-player`;
- `/api/v1/editor/custom-team`;
- `/api/v1/editor/random-team`;
- `/api/v1/editor/random-teams`;
- `/api/v1/editor/assign-player`;
- `/api/v1/editor/remove-player`;
- `/api/v1/editor/free-players`;
- `/api/v1/editor/teams`.

Además, `extractUserIdFromToken` no extrae el usuario del JWT: retorna un UUID fijo. Los endpoints con `userId` explícito no tienen una comprobación uniforme de ownership.

`LeagueTeamCommandController` tampoco tiene guard de perfil ni recibe `Authentication`. Sus operaciones usan el `userId` del body/query directamente:

- `POST /api/v1/world/leagues/{leagueId}/add-team`;
- `DELETE /api/v1/world/leagues/{leagueId}/remove-team/{teamId}`.

La autorización global solo exige que exista algún usuario autenticado; no impide modificar el snapshot de otro usuario.

## 5. Variables de entorno

| Variable | Consumidor | Obligatoria en prod | Default | Secreto | Validación startup |
| --- | --- | --- | --- | --- | --- |
| `DB_HOST` | JDBC/R2DBC/Flyway | Sí | Ninguno | No | Solo presencia |
| `DB_PORT` | JDBC/R2DBC | Sí | Ninguno | No | Solo presencia; no valida número |
| `DB_NAME` | JDBC/R2DBC/Flyway | Sí | Ninguno | No | Solo presencia |
| `DB_USER` | JDBC/R2DBC/Flyway | Sí | Ninguno | No | Solo presencia |
| `DB_PASSWORD` | JDBC/R2DBC/Flyway | Sí | Ninguno | Sí | Presencia y lista de valores inseguros |
| `REDIS_HOST` | Redis custom/Spring | Sí | Ninguno en prod efectivo | No | Solo presencia |
| `REDIS_PORT` | Redis custom/Spring | Sí | Ninguno en prod efectivo | No | Solo presencia; no valida número |
| `REDIS_PASSWORD` | Redis custom/Spring | Sí | Ninguno en prod efectivo | Sí | Presencia y lista de valores inseguros |
| `REDIS_SSL` | Redis custom/Spring | No | `true` en prod | No | No valida coherencia con proveedor |
| `REDIS_USERNAME` | Redis ACL gestionado | No documentada/no consumida | Ninguno | No | No aplica |
| `JWT_SECRET` | `JwtTokenProvider` | Sí | Ninguno | Sí | Presencia y lista de valores inseguros |
| `JWT_EXPIRATION` | JWT | No | 86400000 | No | Conversión numérica de Spring |
| `JWT_REFRESH_EXPIRATION` | JWT | No | 604800000 | No | Conversión numérica de Spring |
| `APP_CORS_ALLOWED_ORIGINS` | `CorsConfig` | Sí | Vacío | No | Presencia; no valida formato/wildcard |
| `PORT` | servidor | No | `SERVER_PORT`/8080 | No | Conversión numérica de Spring |
| `SERVER_PORT` | servidor | No | 8080 | No | Conversión numérica de Spring |

El documento de variables y el código coinciden en las variables principales, pero la documentación menciona TLS/Redis gestionado sin una variable de username ni configuración de ACL en `RedisConfig`.

## 6. Secrets y defaults

### Resultado

El validador productivo bloquea varios valores conocidos, pero el repositorio todavía contiene credenciales de infraestructura como defaults o ejemplos operativos en archivos versionados:

- `src/test/resources/application-test.yml` contiene defaults de DB y Redis.
- `src/test/java/com/footballmanager/application/service/world/importer/ThreeLeagueDatasetImporterTest.java` contiene fallback de DB.
- `src/test/java/com/footballmanager/infrastructure/persistence/Mvp1DatabaseBaselineContractTest.java` contiene fallback de DB.
- `src/test/java/com/footballmanager/application/service/simulation/V25D81InjuryPersistenceDiagnosticTest.java` documenta credenciales de Redis/DB.
- `MANAGER_TEAM_RUNBOOK.md` contiene valores de conexión de test y comandos con credenciales.
- `restart-redis.ps1` contiene credenciales en comentarios operativos.

No se imprimen valores secretos en este informe.

Esto contradice la afirmación amplia de que los secretos reales versionados quedaron removidos. Aunque varios valores sean de test/local, siguen siendo credenciales reutilizables si corresponden a servicios reales y están disponibles para cualquiera con acceso al repositorio.

**Clasificación:** P0 hasta rotar, retirar del historial operativo aplicable y sustituir por variables obligatorias sin fallback.

### Validación JWT

La documentación exige un JWT de al menos 64 bytes para HS512, pero `ProductionStartupValidation` no valida longitud ni entropía. El test de “entorno completo” usa una cadena que no demuestra 64 bytes y pasa.

**Clasificación:** P1. El backend puede pasar el validador y fallar recién al firmar un token con `Keys.hmacShaKeyFor`.

## 7. CORS

Aspectos corregidos:

- se eliminó `@CrossOrigin` disperso;
- `CorsConfig` usa una allowlist configurable;
- no existe wildcard implícito cuando la variable está vacía;
- `allowCredentials` está activado solamente sobre la lista configurada.

Limitaciones:

- `ProductionStartupValidation` no rechaza explícitamente `APP_CORS_ALLOWED_ORIGINS=*`;
- tampoco valida que cada valor sea un origin absoluto válido;
- existe `CorsWebFilter` y además lógica manual de headers CORS para respuestas de seguridad;
- las pruebas son unitarias de parseo y allowlist, no una matriz HTTP de preflight, credentials, métodos y headers en contexto `prod`.

**Clasificación:** P1 de validación/configuración. No se observó wildcard hardcodeado activo en la configuración actual.

## 8. JWT y secretos en runtime

Fortalezas observadas:

- firma HS512;
- expiración y refresh configurables;
- tokens inválidos se rechazan;
- no se encontraron logs explícitos de Authorization header, password o token;
- `WWW-Authenticate: Bearer` se devuelve en 401.

Pendientes:

- longitud mínima de `JWT_SECRET` no validada en startup;
- refresh token es stateless y no se observa revocación/rotación persistida;
- no hay rate limiting de login, registro o refresh;
- registro y login no tienen una política de password visible en el código;
- la diferencia 409 para email existente permite cierta enumeración de cuentas.

**Clasificación:** P1 para beta pública en rate limiting y secrets; P2 para revocación/política de password si el alcance PB1.1 se mantiene limitado.

## 9. Endpoints sensibles

### Correctamente excluidos en `prod`

- admin career;
- admin world;
- seed de mundo;
- debug career;
- test harness y labs.

### Registrados o insuficientemente protegidos en `prod`

- `EditorController`: productivo por ausencia de `@Profile`, con UUID fijo y operaciones mutadoras.
- `LeagueTeamCommandController`: productivo por ausencia de `@Profile`, acepta `userId` del request sin ownership.
- `FieldSubdivisionController` y `FormationController`: endpoints de datos estáticos; quedan bajo autenticación global aunque sus comentarios dicen que son públicos.

El test de perfil no verifica la ausencia de mappings reales desde un `ApplicationContext` `prod`; solo inspecciona anotaciones de una lista incompleta.

**Resultado:** P0 abierto.

## 10. Admin y seed

Los controllers declarados como admin/seed están guardados por `@Profile("!prod")` y/o perfiles no productivos. El `AdminWorldController` tiene comprobación manual de `ROLE_ADMIN` en el código local.

La conclusión no puede extenderse a todo el namespace editor ni a las operaciones de relación liga-equipo, porque esos controllers quedan fuera de la lista de exclusión.

## 11. Frontend production

El commit `8a87b83` agrega `environment.enableDebugRoutes`:

- desarrollo: `true`;
- producción: `false`.

Las pruebas de rutas verifican que el array no contenga `debug/test-harness` cuando está deshabilitado. `apiUrl` de producción es relativo (`/api/v1`), por lo que no depende de localhost ni del proxy Angular local.

La build production actual terminó correctamente, pero el output sigue generando un lazy chunk de `test-harness-page-component` de aproximadamente 565 KB. La ruta queda inaccesible desde el router productivo, pero el código de debug sigue siendo distribuido como artefacto descargable.

**Clasificación:** P1 si la política exige que debug no se distribuya; P2 si solo exige inaccesibilidad funcional. La prueba actual verifica la ruta, no la ausencia del chunk.

## 12. Redis — clasificación

| Dato | Key/patrón | Writer | Reader | TTL | Clasificación | Recuperable desde PostgreSQL |
| --- | --- | --- | --- | --- | --- | --- |
| CareerSave | `career:{userId}` | `RedisCareerRepository` | career services | 30 días | Durable crítico solo Redis | No probado; parcial como máximo |
| Standings | `standing:{userId}:...` | `RedisStandingRepository` | standings/career | 30 días | Durable/cache de runtime | Parcial si fixtures/resultados están completos |
| Detailed match | `career:{careerId}:match-detail:{matchId}` | `DetailedMatchRedisAdapter` | detailed match API | Sin TTL explícito | Durable crítico solo Redis | No demostrado |
| Match state | `match:state:{userId}:{matchId}` | `RedisMatchStateRepository` | match state services | 24 horas | Estado runtime | No demostrado para live completo |
| Runtime match | `runtime:match:{userId}:{matchId}` | `RedisMatchRuntimeRepository` | runtime match services | 2 horas | Sesión efímera crítica | No |
| Baseline compare | `career:{careerId}:match-baseline:{matchId}` | `BaselineStateRedisAdapter` | compare service | 7 días | Debug/compare reconstructible parcial | No crítico para gameplay |
| World snapshot/league/team | Redis repositories y sets asociados | world services | world/editor services | Variable/no uniforme | No resuelto completamente | No demostrado |

La documentación de Redis es honesta al clasificar Redis como runtime crítico, pero eso confirma que la durabilidad primaria todavía está fuera de PostgreSQL. `CareerSave` se guarda solamente en Redis con TTL de 30 días y `DetailedMatchData` se guarda sin TTL explícito en Redis; no hay una prueba de restore desde PostgreSQL que reconstruya toda la carrera, detalle, live session y comandos pendientes.

**Clasificación para beta pública:** P0/P1 restante hasta elegir proveedor persistente y ejecutar restore drill real.

## 13. Redis TLS/configuración

Lo corregido:

- `REDIS_SSL` queda `true` por defecto en `prod`;
- password configurable;
- timeout de comando y shutdown configurados;
- Redis cae en health cuando la operación falla.

Pendientes:

- `RedisConfig` soporta host, port, password y SSL, pero no username ACL;
- no se observa configuración explícita de verificación de certificado o trust material;
- no hay test de Redis caído, credencial inválida, TLS inválido o reconnect;
- `spring.data.redis.password` tiene fallback vacío en `RedisConfig`, aunque el perfil prod debería bloquearlo antes.

**Clasificación:** P1 para proveedor Redis gestionado real.

## 14. Health, liveness y readiness

`/api/v1/health` ejecuta:

- `SELECT 1` contra R2DBC;
- `hasKey` contra Redis;
- responde `UP`/200 solo si ambos caminos terminan correctamente;
- responde `DOWN`/503 si cualquiera falla.

Actuator expone `health,info` y en `prod` oculta los detalles del health.

Limitaciones:

- no se ejecutó una matriz real con PostgreSQL caído, Redis caído, credenciales inválidas y schema incompatible;
- no hay una separación explícita de liveness y readiness en el código;
- no hay prueba de compatibilidad con la ruta de probes de un proveedor concreto;
- la salud custom no escribe la key de prueba, aunque `hasKey` confirma conectividad sin mutación.

**Resultado:** diseño razonable, certificación operativa pendiente.

## 15. Error handling productivo

La configuración global oculta mensajes del error estándar, pero numerosos controllers construyen respuestas explícitas con `e.getMessage()` o `"Internal error: " + e.getMessage()`.

Se encontraron estos patrones en endpoints de auth, detalle, lineup, formación, estilo, sustitución, match control, match engine, versus y seed. Un error de DB/Redis puede terminar exponiendo texto del driver, host, SQL o causa interna directamente al cliente.

**Clasificación:** P1. La configuración `server.error.include-message=never` no corrige respuestas explícitas creadas por los controllers.

## 16. Logging

No se encontraron por búsqueda simple logs que impriman directamente passwords, Authorization headers, cookies o tokens JWT.

Sí se observan logs de:

- `userId`, `careerId`, `matchId` y claves Redis;
- tamaño de palmarés y estado de deserialización;
- mensajes internos de error;
- rutas/patrones de Redis.

Esto puede ser aceptable para debugging operativo, pero falta:

- política de redacción de datos personales;
- correlation/request ID consistente;
- prueba de que DEBUG queda desactivado en el runtime real;
- límites de payload para logs.

**Clasificación:** P2, salvo que los IDs se consideren datos personales en el proveedor elegido.

## 17. Security headers

No se encontró configuración de HSTS, `X-Content-Type-Options`, frame options, Referrer-Policy, CSP ni cache-control de respuestas autenticadas en el backend o frontend.

Esto puede pertenecer al reverse proxy/CDN, pero el despliegue debe documentar quién es responsable. Para una aplicación con tokens y datos de carrera, no puede quedar implícito.

**Clasificación:** P1/P2 según el hosting final; no es motivo único de rechazo PB1.1 si queda delegado formalmente al proxy.

## 18. Login/register

Observado:

- passwords almacenadas mediante `BCryptPasswordEncoder(12)`;
- mensajes de login no distinguen el body de error;
- registro duplicado devuelve conflicto.

No observado:

- rate limiting;
- protección contra brute force;
- política de longitud/complejidad de password;
- bloqueo temporal o backoff;
- revocación de refresh tokens;
- estrategia anti-enumeración completa.

**Clasificación:** P1 para beta pública abierta; correctamente documentado como pendiente de PB1.2 en el checklist, pero no cerrado.

## 19. Graceful shutdown

No se encontró configuración explícita de `spring.lifecycle`, `server.shutdown`, timeout de graceful shutdown, `@PreDestroy` de sesiones live ni un drill de apagado controlado.

El proceso usa schedulers, ejecutores, SSE y sesiones live en memoria. Redis guarda partes del estado, pero la estrategia no demuestra que una live session y sus comandos pendientes sobrevivan a un apagado en mitad de la operación.

**Clasificación:** P1 para runtime cloud; requiere contrato explícito de pérdida/recuperación de live session.

## 20. Tests de hardening

Tests agregados y ejecutados focalizadamente:

- `ProductionStartupValidationTest`;
- `CorsConfigTest`;
- `ProductionEndpointProfileTest`.

Los tres grupos pasaron en la ejecución focalizada de esta auditoría.

Limitaciones reales:

- usan `MockEnvironment` o inspección de anotaciones;
- no levantan el ApplicationContext productivo completo;
- no prueban ausencia real de mappings para `EditorController` y `LeagueTeamCommandController`;
- no prueban Redis TLS/credenciales caídas;
- no prueban error bodies productivos;
- no prueban headers;
- no prueban una build de frontend sin el chunk debug.

## 21. Backend suite

Validación independiente realizada:

- `mvn -q -Dtest=ProductionStartupValidationTest,CorsConfigTest,ProductionEndpointProfileTest test`: OK.
- La documentación declara `2536` tests, 0 failures, 0 errors y 4 skipped.
- No se revalidó de forma independiente una corrida completa con salida final de 2536 en esta auditoría.

Por lo tanto, el resultado declarado se conserva como evidencia de cierre de Codex, pero no se usa para ocultar los P0 de configuración encontrados.

## 22. Frontend suite

Validación independiente realizada:

- build production: OK;
- la ruta productiva no incorpora `debug/test-harness` al array de rutas cuando `enableDebugRoutes=false`;
- el build sigue generando un lazy chunk del test harness;
- no se revalidó en esta auditoría el conteo completo de 1028 tests.

La documentación declara 1028 SUCCESS, 0 failures y 2 skipped. El número declarado queda pendiente de repetición aislada para una certificación reproducible.

## 23. Documentación

La documentación de hardening conserva la auditoría histórica y distingue varios gates PB1.2. Eso es correcto.

Sin embargo, hay afirmaciones que deben corregirse antes de poder usarla como certificación:

- “secretos reales versionados removidos” no contempla defaults versionados en test/runbooks/scripts;
- “admin/seed/debug/test harness fuera de prod” no contempla editor y league-team commands;
- “Redis resuelto documentalmente” no equivale a durabilidad ni restore probado;
- el checklist no menciona el UUID fijo de editor ni ownership de LeagueTeamCommandController;
- la build production no se evalúa por presencia de chunks de debug.

## 24. P0 cerrados

Los siguientes puntos sí muestran corrección parcial o completa:

- CORS wildcard hardcodeado en controllers: corregido y centralizado.
- perfil `prod`: creado.
- controllers explícitos de admin/seed/test harness: guardados por profile.
- defaults productivos principales en `application.yaml`: retirados.
- health custom DB/Redis: implementado con 503 ante caída.
- logging base y detalles Actuator: endurecidos.

Estos cierres no compensan los P0 abiertos de la sección siguiente.

## 25. P0 restantes

1. `EditorController` está registrado en `prod`, contiene un UUID fijo y expone mutaciones/lecturas de world data con ownership insuficiente.
2. `LeagueTeamCommandController` está registrado en `prod` y permite operar sobre `userId` tomado del request sin comparar contra el JWT.
3. Credenciales de DB/Redis versionadas en `application-test.yml`, tests, runbook y script operativo. Deben rotarse y retirarse como defaults; no basta con que no estén en `application-prod.yml`.
4. Redis mantiene datos funcionales críticos de carrera y detalle sin durabilidad primaria probada fuera de Redis. Para beta pública, no hay restore drill real.

## 26. P1 restantes

1. `JWT_SECRET` no valida longitud mínima ni entropía, aunque la documentación exige 64 bytes para HS512.
2. Controllers devuelven `e.getMessage()` y detalles internos en respuestas HTTP explícitas.
3. No hay rate limiting ni protección brute-force en login/register/refresh.
4. Redis TLS no cubre username ACL ni una política explícita de verificación de certificados/proveedor.
5. No hay graceful shutdown probado para LiveSession, SSE y comandos pendientes.
6. No existe una matriz de pruebas real de health/readiness con DB, Redis, credenciales y schema incompatibles.
7. El chunk del test harness sigue presente en el build production; la ruta está deshabilitada, pero el código se distribuye.

## 27. P2

- Security headers no configurados en aplicación; responsabilidad del proxy no documentada por entorno.
- Falta de correlation/request ID.
- CORS no valida formato de origin ni rechaza wildcard en startup.
- Enumeración parcial por status de registro duplicado.
- Política de password no documentada/implementada.
- Logs con IDs y claves de Redis requieren política de privacidad.
- Suite full y matriz HTTP de hardening no repetidas de forma independiente.

## 28. Preparación para PB1.2

PB1.2 debe comenzar solamente después de cerrar los P0 de este informe.

Gates prioritarios:

1. sacar `EditorController` de `prod` o implementar autorización real y eliminar UUID fijo;
2. proteger `LeagueTeamCommandController` con usuario autenticado y ownership/rol correcto;
3. rotar y retirar credenciales versionadas y fallbacks de test/runbooks/scripts;
4. definir proveedor Redis persistente y ejecutar backup/restore drill;
5. mover durabilidad primaria de career/detail/standings a PostgreSQL o documentar un restore verificable;
6. endurecer validación de JWT/CORS y sanitizar error bodies;
7. agregar rate limiting y graceful shutdown;
8. definir headers en proxy/CDN y verificar runtime real.

Los siguientes puntos permanecen deliberadamente fuera del alcance de PB1.1, pero siguen siendo gates PB1.2: Docker/buildpack/cloud artifact, CI/CD, backups automáticos, restore drill, smoke contra proveedor real y SSE detrás de proxy/CDN.

## 29. Conclusión

El trabajo de hardening mejoró de forma real el perfil productivo, CORS, los endpoints explícitos de seed/admin/debug y el health. Los tests focalizados pasan, el frontend production compila y el arranque sin variables no continúa.

No obstante, la auditoría independiente encuentra exposición funcional y de datos que impide aprobar PB1.1: dos familias de endpoints mutadores permanecen cargadas en prod sin ownership suficiente y hay credenciales versionadas fuera del perfil productivo. La clasificación correcta es `PB1.1 HARDENING REJECTED` hasta cerrar esos bloqueantes.
## PB1.1 final remediation addendum - 2026-07-31

Post-audit remediation closed the P0 application hardening findings. See:

- `docs/deployment/PB1_PRODUCTION_HARDENING_FINAL_REMEDIATION.md`
- `docs/deployment/PB1_PRODUCTION_HARDENING_FINAL_REVIEW.md`
- `docs/deployment/PB1_SECRET_ROTATION_AND_REPOSITORY_HYGIENE.md`
- `docs/deployment/PRODUCTION_SECURITY_HEADERS.md`
- `docs/deployment/PB1_REMAINING_INFRASTRUCTURE_GATES.md`

Validation evidence:

- Backend: 2548 tests, 0 failures, 0 errors, 4 skipped.
- Frontend: 1029 SUCCESS, 0 failures, 2 skipped.
- Production frontend artifact inspection: no test harness route/chunk/text in `dist/demo`.

Historical rejected verdicts remain preserved in their original reports; the remediation is recorded separately.
