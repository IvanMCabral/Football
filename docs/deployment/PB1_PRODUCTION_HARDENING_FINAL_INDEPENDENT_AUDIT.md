# MANAGER — PB1.1 Final Production Hardening Independent Re-Audit

Fecha de re-auditoría: 2026-08-01
Alcance: re-auditoría independiente del estado posterior a los commits PB1.1 declarados.
Modo: solo lectura sobre código, configuración, historial, artefactos y tests; el único archivo creado por esta tarea es este informe.

## 1. Veredicto

`PB1.1 HARDENING REJECTED`

La remediación cerró correctamente la exposición de `EditorController`, los mutadores de relación liga-equipo y el debug route del frontend productivo. Sin embargo, quedan bloqueantes verificables:

- Existen rutas productivas que siguen serializando mensajes derivados de `getMessage()`.
- La suite backend completa no pasa en la evidencia reproducible actual: `2553 tests, 0 failures, 200 errors, 4 skipped`.
- El health check Redis consulta la existencia de una clave que el código no crea; por sí solo no prueba disponibilidad y puede devolver `DOWN` con Redis operativo.
- No existe evidencia de Redis gestionado, restore drill, pérdida/reconexión ni runtime productivo.

Por esas razones no corresponde convertir la aprobación declarada en una aprobación independiente ni declarar listo el MVP1 para exposición pública.

## 2. Commits auditados

### Root/backend

- `25e76f61 Secure remaining production endpoints`
- `7b0ff46f Remove repository credential defaults`
- `f342c4e0 Harden production validation and errors`
- `fb8edf99 Close PB1.1 hardening remediation`

Contexto previo revisado para detectar regresiones: `0b109a5a`, `9d55adcf` y `975a8a68`.

### Frontend

- `b33cc11 Exclude debug tooling from production frontend`

## 3. Git y alcance de cambios

- Root: limpio antes de iniciar la re-auditoría.
- Frontend `front-ciber/project`: limpio antes de iniciar la re-auditoría.
- `git diff --check` del árbol actual: sin errores.
- El rango histórico `0b109a5a..fb8edf99` conserva dos advertencias de trailing whitespace en el documento histórico `PB1_PRODUCTION_HARDENING_INDEPENDENT_AUDIT.md`; no afecta al código actual.
- No se modificaron código, tests, configuración, secretos, historial ni commits. Este informe es el único artefacto nuevo de la tarea.

## 4. Auditoría histórica preservada

El documento histórico `docs/deployment/PB1_PRODUCTION_HARDENING_INDEPENDENT_AUDIT.md` conserva explícitamente el veredicto `PB1.1 HARDENING REJECTED`. No fue sobrescrito ni convertido en aprobación retrospectiva.

## 5. Contexto productivo real

`ProductionMappingContextTest` arranca un `SpringBootTest` real con perfil `prod`, servidor WebFlux en puerto aleatorio, PostgreSQL deshabilitando solo Flyway y Redis configurado para el contexto. La prueba terminó correctamente.

Configuración productiva observada:

- `spring.profiles.active=prod` activa el bloque `application-prod.yml`.
- `server.shutdown=graceful`.
- `spring.lifecycle.timeout-per-shutdown-phase=30s` por defecto.
- `management.endpoint.health.show-details=never` en `prod`.
- Endpoints de tooling y seed restringidos por perfil o deshabilitados por configuración productiva.

La prueba de contexto demuestra el registro real de mappings, pero no sustituye una prueba de despliegue con proveedor, proxy, TLS, Redis gestionado y health probe externo.

## 6. EditorController

Estado: `PASS` para el bloqueo original.

En [EditorController.java](<D:/ProyectosOpenCode/MANAGER/src/main/java/com/footballmanager/adapters/in/web/editor/EditorController.java>) `/api/v1/editor/**` está restringido a perfiles `dev`, `local` y `test`. El UUID fijo fue eliminado; las operaciones usan el `Authentication` y convierten el principal a UUID.

La prueba de contexto productivo verifica que no existe ningún mapping que empiece por `/api/v1/editor`. `ProductionEndpointProfileTest` también exige la guardia de perfil.

## 7. LeagueTeamCommandController

Estado: `PASS` para el bloqueo original.

En [LeagueTeamCommandController.java](<D:/ProyectosOpenCode/MANAGER/src/main/java/com/footballmanager/adapters/in/web/league/LeagueTeamCommandController.java>) el controlador solo carga en `dev`, `local` y `test`. El usuario autenticado es la fuente autoritativa; el `userId` legacy solo se acepta si coincide y se rechaza si difiere.

La prueba de contexto productivo confirma que no se registran los mutadores `add-team` ni `remove-team`.

## 8. `/api/v1/lineup-editor/**`

Estado: `PASS` con alcance acotado.

- `/api/v1/lineup-editor/subdivisions` expone datos estáticos de subdivisiones.
- `/api/v1/lineup-editor/formations` expone datos estáticos de formaciones.
- No reciben `userId`, no mutan carrera y no sustituyen los endpoints autenticados de `/api/v1/career/lineup/**`.
- La lógica de carrera usa `ControllerHelper` y `Authentication`.

Estos endpoints públicos son razonables como catálogo estático, pero no están cubiertos por una prueba independiente de autorización HTTP; el contexto solo verifica la ausencia de los mutadores sensibles.

## 9. Secretos en el working tree

Estado: `PASS PARCIAL`.

- No se encontraron valores operativos reales impresos ni hardcodeados en la configuración productiva actual.
- `application.yaml` exige placeholders para DB, Redis, JWT y CORS.
- `.env.example` contiene placeholders de desarrollo, no secretos operativos; no debe usarse como configuración productiva.
- `src/test/resources/application-test.yml` exige `DB_PASSWORD` y `REDIS_PASSWORD` por entorno, pero conserva nombres/hosts locales de test.
- `ProductionMappingContextTest` usa valores sintéticos embebidos solo para levantar el contexto de prueba.
- El `.env` local no fue incluido ni impreso en el informe.

La eliminación de defaults activos está implementada, pero la suite de integración no es autosuficiente sin Redis disponible y variables de entorno.

## 10. Secretos en el historial

Estado: `FAIL OPERATIVO`.

El historial reachable contiene commits anteriores que versionaron defaults o instrucciones con credenciales locales, entre ellos los antecedentes de `application-test.yml`, runbook y helper de Redis. El commit `7b0ff46f` elimina los usos activos, pero no reescribe el historial.

La política documentada en `PB1_SECRET_ROTATION_AND_REPOSITORY_HYGIENE.md` es correcta: cualquier credencial previamente committeada debe considerarse comprometida y rotarse antes de usarla fuera de esta estación. No hay evidencia en este repositorio de que la rotación efectiva se haya ejecutado en todos los proveedores.

## 11. Variables y validación de producción

Estado: `PASS PARCIAL`.

`ProductionStartupValidation` bloquea el perfil `prod` ante variables requeridas ausentes, vacías o conocidas como inseguras. También valida:

- `JWT_SECRET` con mínimo de 64 bytes UTF-8 y sin espacios extremos.
- `JWT_EXPIRATION` y `JWT_REFRESH_EXPIRATION` dentro de rangos positivos.
- `DB_PORT` y `REDIS_PORT` positivos.
- CORS con origen explícito, esquema HTTP/HTTPS, sin wildcard, path, query, fragment ni `null`.

Limitaciones:

- No hay validación de entropía real de `JWT_SECRET`; una cadena repetitiva de 64 bytes es aceptada.
- `REDIS_SSL=false` puede sobreescribir el default seguro de `application-prod.yml`; no hay fail-closed que obligue TLS en toda configuración productiva.
- No se valida que el host productivo no sea localhost ni que el proveedor cumpla persistencia.

## 12. CORS

Estado: `PASS` para el allowlist básico.

`CorsConfig` usa `allowedOrigins` explícitos, `allowCredentials=true` y no usa `*`. El validador productivo rechaza wildcard completo, wildcard parcial, origen vacío, `null`, falta de esquema, paths y valores malformados.

La respuesta de error de SecurityConfig agrega headers CORS para orígenes permitidos. Falta una prueba productiva end-to-end detrás del proxy real que confirme preflight, credenciales, headers expuestos y comportamiento con el dominio definitivo.

## 13. JWT

Estado: `PASS PARCIAL`.

- El filtro autentica mediante `Authorization: Bearer` y usa el principal del token.
- El startup validator rechaza 63 bytes y acepta 64 bytes; esos casos están cubiertos por `ProductionStartupValidationTest`.
- Expiración y refresh expiration tienen límites.
- El password encoder es BCrypt con cost 12.

Gap: la prueba de aceptación usa una cadena repetida de 64 caracteres y el validator solo mide longitud. Para producción debe exigirse un secreto generado de alta entropía y rotación documentada, no solamente longitud.

## 14. Rate limiting

Estado: `PASS MÍNIMO / NO DISTRIBUIDO`.

`RateLimitingWebFilter` limita login, register y refresh a 20 requests por minuto por defecto y responde 429. El test específico pasa.

Limitaciones para Internet:

- El contador vive en memoria y no coordina múltiples instancias.
- La clave prioriza el primer `X-Forwarded-For`; sin un proxy confiable puede ser falsificada para evadir el límite.
- No hay evidencia de `Retry-After`, almacenamiento distribuido ni límites diferenciados por cuenta/IP.

Es suficiente como defensa mínima de una beta controlada, no como control de abuso de una exposición pública amplia.

## 15. Password policy

Estado: `PASS MÍNIMO`.

El registro rechaza password vacío, blanco, menor a 8 o mayor a 128 caracteres. Login rechaza forma inválida. El hash usa BCrypt cost 12.

No hay verificación de password comprometido, historial, MFA ni política de bloqueo por cuenta; no son parte del cierre mínimo, pero deben considerarse antes de una beta amplia.

## 16. Errores y exposición de mensajes internos

Estado: `FAIL — BLOQUEANTE`.

La remediación declara sanitización productiva, pero el código actual todavía expone mensajes derivados de excepciones en rutas productivas:

- [GlobalExceptionHandler.java](<D:/ProyectosOpenCode/MANAGER/src/main/java/com/footballmanager/adapters/in/web/common/GlobalExceptionHandler.java:42>) serializa `ex.getMessage()` para errores de lineup, estado, autorización e impersonación.
- [LineupController.java](<D:/ProyectosOpenCode/MANAGER/src/main/java/com/footballmanager/adapters/in/web/career/controllers/LineupController.java:229>) y sus handlers de preview serializan `ex.getMessage()`.
- [MatchControllerReactive.java](<D:/ProyectosOpenCode/MANAGER/src/main/java/com/footballmanager/adapters/in/web/versus/MatchControllerReactive.java:152>) concatena el mensaje de un `IllegalArgumentException` en la respuesta.
- [AdvanceRoundUseCaseImpl.java](<D:/ProyectosOpenCode/MANAGER/src/main/java/com/footballmanager/application/service/career/AdvanceRoundUseCaseImpl.java:78>) incorpora `e.getMessage()` al resultado de negocio que puede llegar al cliente.

`PublicErrorMessageResolver` se usa en algunos controllers, pero no es transversal ni está aplicado al `RestControllerAdvice` ni a todos los caminos de negocio. La claim de `PB1_PRODUCTION_HARDENING_FINAL_REVIEW.md` (“Production errors sanitized”) no coincide con la evidencia actual.

## 17. Correlation ID

Estado: `PASS BÁSICO`.

`RequestCorrelationWebFilter` genera o propaga `X-Request-Id`, lo limita a 80 caracteres, lo devuelve en la respuesta y lo coloca en MDC. El test específico verifica propagación y headers mínimos.

Limitaciones: no hay evidencia de propagación validada a logs asíncronos Reactor ni a servicios externos; el uso de MDC debe validarse con el stack de observabilidad real.

## 18. Redis: configuración

Estado: `PASS PARCIAL / NO LISTO PARA PRODUCCIÓN`.

`RedisConfig` soporta host, port, username ACL, password, SSL y timeouts Lettuce. `application-prod.yml` activa SSL por defecto.

Gaps comprobados:

- La configuración custom de [RedisConfig.java](<D:/ProyectosOpenCode/MANAGER/src/main/java/com/footballmanager/infrastructure/config/RedisConfig.java>) no mapea `spring.data.redis.database` a `RedisStandaloneConfiguration`; por tanto, la afirmación del perfil test de aislar Redis en database 15 no queda respaldada por este bean.
- Redis no está respaldado por un proveedor gestionado probado en esta auditoría.
- La suite completa no pudo conectarse al Redis local disponible en el entorno de verificación.
- No se ejecutó restore, pérdida, reconexión ni failover.

## 19. Redis: clasificación y durabilidad

Estado: `HONESTO PERO INCOMPLETO`.

La documentación clasifica correctamente Redis como runtime crítico, no como cache descartable:

- `career:{userId}`: carrera activa, TTL observado de 30 días; durable y no reconstruible de forma completa.
- standings: TTL observado de 30 días; reconstrucción parcial condicionada a PostgreSQL.
- detailed match: key de detalle sin TTL explícito visible en `DetailedMatchRedisAdapter`; durable/reconstructible parcial.
- runtime match: TTL de 2 horas; estado efímero crítico durante un partido.
- match commands: TTL de 24 horas.
- baseline: TTL de 7 días; reconstructible/no crítico para producción.

La documentación no afirma que el restore ya esté probado, lo cual es correcto. Por eso, la durabilidad de carrera no puede considerarse cerrada.

## 20. Health y readiness

Estado: `FAIL FUNCIONAL / NO PROBADO COMO READINESS`.

`HealthController` ejecuta `SELECT 1` contra PostgreSQL y consulta:

```java
redisTemplate.hasKey("__manager_healthcheck__")
```

El código no crea esa clave en ningún lugar observado. Redis puede estar operativo y aun así devolver `false` porque la clave no existe; en ese caso el endpoint responde `DOWN` y HTTP 503. No hay un test de `HealthController` que cubra Redis operativo, Redis caído y el valor del probe.

La respuesta 503 con Redis fallando es correcta como intención, pero el criterio de “Redis up” está implementado como existencia de una clave ausente, no como ping/operación validada. El health check no es evidencia suficiente para Cloud Run/Firebase ni para un readiness probe externo.

## 21. Graceful shutdown

Estado: `PASS CONFIGURACIÓN / NO DRILL`.

Existe `server.shutdown=graceful`, timeout de fase de 30 segundos y timeout de shutdown Lettuce de 5 segundos. No hay evidencia de un drill productivo que confirme drenaje de SSE, cierre de LiveSession, persistencia final de carrera y comportamiento bajo timeout.

## 22. Headers de seguridad

Estado: `PASS BÁSICO / PB1.2 PENDIENTE`.

El backend agrega:

- `X-Request-Id`.
- `X-Content-Type-Options: nosniff`.
- `X-Frame-Options: DENY`.
- `Cache-Control: no-store`.

HSTS, CSP, Referrer-Policy, `frame-ancestors` y política de cache de assets están documentados como responsabilidad del CDN/proxy. Mientras no exista dominio HTTPS y configuración edge probada, no se puede declarar hardening completo de navegador.

## 23. Artefacto frontend productivo

Estado: `PASS`.

- `npm run build`: PASS.
- La configuración productiva reemplaza `app.debug-routes.ts` por `app.debug-routes.prod.ts`.
- El build productivo no contiene `test-harness`, `TestHarnessPageComponent`, `debug/test-harness` ni referencias a `features/debug` en `dist/demo`.
- Los tests de rutas prod cubren la lista vacía de debug routes.

## 24. Tests específicos de hardening

Estado: `PASS PARCIAL`.

Pasaron:

- `ProductionEndpointProfileTest`.
- `ProductionStartupValidationTest`.
- `RateLimitingWebFilterTest`.
- `RequestCorrelationWebFilterTest`.
- `CorsConfigTest`.
- `ProductionMappingContextTest` con contexto Spring real y perfil `prod`.
- `mvn -q -DskipTests test-compile`.

No existe una prueba específica que detecte mensajes internos en `GlobalExceptionHandler`, `LineupController`, `MatchControllerReactive` o resultados de avance, ni una prueba funcional del health Redis.

## 25. Suite backend completa

Estado: `FAIL`.

Dos ejecuciones independientes:

1. Sin variables exportadas: varios contextos no cargan por placeholder obligatorio `REDIS_PASSWORD`.
2. Cargando las variables presentes en `.env` sin imprimir valores: los contextos arrancan, pero Redis en `localhost:6379` rechaza la conexión durante los hooks de limpieza.

Resultado reproducido de la segunda ejecución: `Tests run: 2553, Failures: 0, Errors: 200, Skipped: 4`.

La cifra declarada de `2548 tests, 0 failures, 0 errors, 4 skipped` en la documentación no es reproducible en el estado y entorno actual. Los errores están dominados por Redis no disponible, pero siguen impidiendo afirmar suite verde.

## 26. Suite frontend completa

Estado: `PASS`.

- Guard de encoding: `385 files scanned`, PASS.
- Karma ChromeHeadless: `1029 SUCCESS`, `0 failures`, `2 skipped`.
- Build productivo: PASS.

Se observaron warnings de pruebas sobre SSE 404, pero no produjeron fallos.

## 27. Documentación y claims

Estado: `FAIL DE CONSISTENCIA`.

Correcto:

- El documento histórico rechazado se preserva.
- `PB1_SECRET_ROTATION_AND_REPOSITORY_HYGIENE.md` exige rotación de cualquier credencial histórica.
- `PB1_REMAINING_INFRASTRUCTURE_GATES.md` reconoce que Redis, restore, deploy, edge y CI/CD quedan pendientes.
- `REDIS_RUNTIME_STRATEGY.md` no presenta Redis como cache descartable.

Incorrecto o no respaldado por esta re-auditoría:

- `PB1_PRODUCTION_HARDENING_FINAL_REVIEW.md` declara `Production errors sanitized`, pero permanecen respuestas con `getMessage()`.
- Declara suites backend verdes, mientras la ejecución actual produce 200 errors.
- Declara health/readiness cerrado a nivel PB1.1 sin prueba funcional del probe Redis.

## 28. P0 abiertos

### P0-1 — Mensajes internos todavía expuestos

Corregir todos los caminos de respuesta productivos que serializan `getMessage()` y centralizar el mapeo en mensajes públicos/códigos estables. Agregar tests que activen errores de validación, estado, autorización y fallos inesperados bajo perfil `prod`.

### P0-2 — Suite backend no verde

Restaurar una infraestructura de test reproducible y ejecutar la suite completa con Redis disponible, autenticado y aislado. El resultado debe ser 0 failures y 0 errors antes de reemitir una aprobación.

## 29. P1 abiertos

- Corregir el probe de Redis del health check y agregar tests UP/DOWN; la comprobación debe validar conectividad, no existencia de una clave nunca creada.
- Mapear explícitamente `spring.data.redis.database` o eliminar la afirmación de aislamiento de database 15.
- Exigir alta entropía de JWT, no solo 64 bytes.
- Hacer rate limiting distribuido o documentar límite estricto a una sola instancia detrás de proxy confiable.
- Ejecutar restore drill, Redis loss/reconnect drill y política TTL por familia.
- Ejecutar graceful shutdown drill con SSE/LiveSession.
- Configurar y probar HSTS/CSP/Referrer-Policy en el edge definitivo.
- Reconciliar la documentación de aprobación con los resultados verificables actuales.

## 30. Gates PB1.2

Siguen pendientes y bloquean una beta pública amplia:

- Proveedor gestionado de PostgreSQL/Redis con backups y credenciales rotadas.
- Redis persistente, restore drill medido, pérdida/reconexión y clasificación de datos recuperables.
- Docker/buildpack y hardening de imagen.
- CI/CD con gates de test, scan y deploy.
- Mapping real de health/readiness en el proveedor.
- Dominio definitivo, HTTPS, HSTS, CSP y políticas del CDN/proxy.
- Drill de graceful shutdown y validación de SSE detrás del proxy.
- RTO/RPO documentados y medidos.

## 31. Readiness

`NO APTO para salida de MVP1 como beta pública.`

`APTO únicamente para continuar remediación controlada en entorno no público`, sujeto a corregir los P0 y repetir la auditoría completa.

## 32. Conclusión

Los cierres de aislamiento de endpoints sensibles y frontend debug son válidos. La configuración de secretos activos mejoró y las defensas mínimas de JWT, CORS, rate limit, correlación y headers existen.

La aprobación final no puede emitirse porque el contrato de errores sigue incumplido, la suite backend está roja por infraestructura Redis no disponible y el health check Redis no representa correctamente disponibilidad. La próxima aprobación requiere evidencia reproducible de esos fixes, suite completa verde, rotación efectiva de secretos históricos y ejecución de los gates operativos definidos para PB1.2.
