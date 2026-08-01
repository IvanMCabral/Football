# PB1.2.1 — Reauditoría independiente definitiva de runtime P0

Fecha: 2026-08-01

Alcance: verificación independiente de la remediación posterior al rechazo anterior. La auditoría fue de solo lectura sobre código, configuración, runner, artefactos y evidencia local. Docker no está instalado y no se intentó instalarlo.

## 1. Veredicto

**PB1.2.1 PRODUCTION RUNTIME REJECTED**

La remediación cerró los P0 de logging, base runtime, healthcheck, binding, Redis SSL, contexto Docker, reproducibilidad y arranque funcional del JAR. Sin embargo, el P0 de shutdown no está cerrado con evidencia válida: el runner termina la aplicación mediante `Stop-Process`, que no es una señal SIGTERM/graceful equivalente, y su `finally` usa `Stop-Process -Force` para limpiar procesos. El resultado PASS demuestra que los procesos dejaron de existir, pero no que Spring haya ejecutado graceful shutdown ni que el flujo de terminación sea válido para producción.

## 2. Commits auditados

- `e354109a Fix container logging and runtime binding`
- `f5a60e41 Validate production jar startup and shutdown`
- `3b41073a Close PB1.2.1 runtime P0 findings`

Orden observado en el log: `3b41073a`, `f5a60e41`, `e354109a`, seguido por `41a55161` y los commits previos de runtime.

## 3. Estado Git

Antes de crear este reporte, el root y `front-ciber/project` estaban limpios. No se observaron cambios de gameplay, simulación o datasets en el rango `41a55161..3b41073a`, y no se encontraron secretos reales nuevos en los cambios inspeccionados.

`git diff --check` del working tree no reportó errores. La comparación histórica `git diff --check 41a55161..3b41073a` sí reporta dos líneas con trailing whitespace en el reporte histórico `PB12_PRODUCTION_RUNTIME_ARTIFACTS_INDEPENDENT_AUDIT.md`; no se modificó ese documento.

## 4. Logging productivo

El cierre es correcto estáticamente:

- `src/main/resources/logback.xml` contiene únicamente `ConsoleAppender` hacia `System.out`;
- encoder UTF-8;
- root configurable con default INFO;
- nivel de aplicación configurable con default INFO;
- request ID mediante `%X{requestId:-no-request-id}`;
- no hay `RollingFileAppender`, `FileAppender`, `<file>`, `D:/`, `C:/` ni root DEBUG;
- `logging.pattern.file` fue eliminado de `application.yaml`.

La búsqueda en recursos productivos no encontró configuración de escritura local. No se observaron archivos backend nuevos o modificados durante el smoke en `logs/`, `app.log` ni `ciberfootbolt_local/logs`. Esos directorios contienen archivos históricos previos y fueron tratados como baseline.

## 5. Dockerfile

El Dockerfile final tiene:

- build multi-stage Maven/Temurin 21;
- runtime `eclipse-temurin:21.0.8_9-jre-jammy`;
- instalación explícita de `curl`, `ca-certificates` y `tzdata`;
- limpieza de `/var/lib/apt/lists/*`;
- usuario/grupo de sistema `manager`;
- `COPY --chown=manager:manager` del JAR;
- `WORKDIR /app`, `USER manager` y `EXPOSE 8080`;
- `ENTRYPOINT` con `exec java`;
- perfil `prod` por defecto;
- healthcheck sin autenticación sobre liveness.

La sintaxis y las expansiones son coherentes estáticamente. No se declara build real porque Docker no está disponible.

## 6. Base runtime Jammy

La etiqueta Temurin Java 21 Jammy es explícita y no usa `latest`. La instalación de CA, timezone y curl es adecuada para el contrato declarado. El entorno local no puede confirmar capas, packages efectivos, DNS, CA dentro de la imagen ni dependencias del JRE Jammy.

No quedan dependencias Alpine declaradas en el Dockerfile. Esta parte queda aprobada estáticamente, condicionada a la futura build externa.

## 7. PORT y SERVER_ADDRESS

La configuración real es:

```yaml
server:
  address: ${SERVER_ADDRESS:0.0.0.0}
  port: ${PORT:${SERVER_PORT:8080}}
```

El smoke ejecutado usó `PORT=61748` y `SERVER_ADDRESS=0.0.0.0`; liveness y readiness respondieron en ese puerto. La guard test valida las dos expresiones.

No se ejecutaron variantes runtime separadas para fallback exclusivo `SERVER_PORT`, ausencia de ambas variables ni puerto inválido. Esas variantes quedan como evidencia P1 pendiente; la configuración estática y el caso de puerto no estándar sí son correctos.

## 8. Redis SSL

El contrato público está unificado en `REDIS_SSL`:

- base: `${REDIS_SSL:false}`;
- perfil prod: `${REDIS_SSL:true}`;
- runner: `REDIS_SSL=false` para Redis temporal sin TLS;
- documentación operativa: `REDIS_SSL`;
- no hay alias activo `REDIS_SSL_ENABLED` en código/configuración/documentación vigente.

Las referencias `spring.data.redis.ssl.enabled` restantes son la propiedad interna de Spring y tests de infraestructura, no una segunda variable pública. Esta remediación queda aprobada.

## 9. Contexto Docker

`.dockerignore` ahora excluye `docs/`, markdown raíz, `front-ciber/`, logs, backups, dumps, `target/`, reportes de tests, dependencias frontend, IDE, temporales, secretos, `.env` y Git.

El contexto backend requerido observado (`pom.xml` más `src`) es de **1027 archivos y 12.678.689 bytes**, incluyendo recursos productivos, migraciones y dataset requerido. No se puede medir el contexto Docker real sin Docker, pero la exclusión estática deja el contexto razonablemente reducido.

## 10. Reproducibilidad del JAR

Se ejecutaron dos veces, desde `mvn -q clean package -DskipTests`:

- build 1: exit code 0, `42.467.597` bytes, `1287` entradas, SHA-256 `F7C0C609A97418C9FFDE36821CE5CD7EA0562C32B74EAA6504C9BF1DC08F9FB1`;
- build 2: exit code 0, `42.467.597` bytes, `1287` entradas, SHA-256 `F7C0C609A97418C9FFDE36821CE5CD7EA0562C32B74EAA6504C9BF1DC08F9FB1`.

El manifest fue inspeccionado en el artefacto final y el hash completo coincidió entre ambas builds. La propiedad `project.build.outputTimestamp` explica el cierre de la variabilidad anterior. Reproducibilidad aprobada.

## 11. Smoke runner

`tools/run-production-jar-smoke.ps1`:

- no carga `.env`;
- deriva el root desde `$PSScriptRoot`, sin rutas personales hardcodeadas;
- crea PostgreSQL temporal en puerto aleatorio;
- crea usuario y base con nombres efímeros;
- crea Redis temporal en puerto aleatorio con password;
- genera JWT aleatorio de 96 bytes;
- activa `SPRING_PROFILES_ACTIVE=prod`;
- usa puerto HTTP no estándar y `SERVER_ADDRESS=0.0.0.0`;
- captura stdout/stderr en el workspace temporal, sin imprimir credenciales/tokens;
- detiene la aplicación y las dependencias si termina normalmente o falla.

Limitaciones detectadas del runner:

1. No elimina `$work` al finalizar; el smoke dejó un directorio temporal con logs de evidencia y `postgres-data`.
2. No conserva en el resultado los PIDs, startup duration ni exit codes individuales.
3. El paso de carrera es condicional: si la consulta de IDs no devuelve filas, deja `careerCreated=false` pero puede emitir `status=PASS`; el runner no falla de forma obligatoria ante ese fallo.
4. No ejecuta un segundo arranque contra la misma base temporal.
5. La comprobación de archivos locales se limita a `logs` y `app.log` en el root; no cubre todos los directorios posibles ni limpia temporales.

Estos son problemas P1 del instrumento de evidencia, no un fallo del caso PASS observado.

## 12. Smoke real del JAR

Se ejecutó:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\run-production-jar-smoke.ps1 -SkipBuild
```

Resultado de la ejecución:

```json
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":61748,"serverAddress":"0.0.0.0","liveness":200,"readiness":200,"registered":true,"login":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"localLogArtifacts":0,"shutdownMs":51,"postgresTemp":true,"redisTemp":true}
```

El PostgreSQL temporal usó el puerto `61746` y Redis el puerto `61747`. Después del runner no quedó proceso Java/Redis del smoke y los puertos `61746`, `61747` y `61748` quedaron libres. Los procesos PostgreSQL preexistentes del entorno no fueron considerados parte del smoke.

El runner terminó con exit code 0. No se imprimieron passwords ni tokens. La duración de startup y los PIDs no fueron registrados por el runner.

## 13. Liveness y readiness

El smoke confirmó:

- liveness HTTP 200 con estado UP;
- readiness HTTP 200 con PostgreSQL y Redis temporales disponibles;
- ambos endpoints sin autenticación;
- mismo puerto configurable;
- ninguna evidencia de detalles internos en el resultado.

La matriz de readiness y liveness DOWN continúa cubierta por tests existentes. Docker healthcheck queda pendiente de ejecución dentro de imagen, pero su comando es estáticamente válido con curl instalado.

## 14. Auth y carrera mínima

El smoke confirmó, contra la base temporal:

- register con access token;
- login con access token;
- `/api/v1/auth/me` con token de login;
- creación de carrera mínima mediante `/api/v1/games`;
- selección de IDs desde la base temporal;
- sin bypass de admin/debug.

El caso observado terminó con `careerCreated=true`. La limitación es que el runner no falla si el conjunto de IDs queda vacío, como se detalla en la sección del runner.

## 15. Flyway

El smoke confirmó una migración exitosa en `flyway_schema_history` (`1`). La configuración mantiene `baseline-on-migrate=false` y no hay comandos `repair` ni `clean` en el runner.

La prueba fue sobre una base PostgreSQL temporal y no utilizó la base principal. No se verificó un segundo arranque del mismo JAR contra la misma base temporal, por lo que la idempotencia operativa de un segundo startup queda pendiente como P1.

## 16. Shutdown

El resultado observado fue `shutdownMs=51`, con proceso Java terminado y dependencias detenidas por el bloque `finally`. También se comprobó externamente que no quedaron procesos/puertos del smoke.

Esto **no constituye una prueba válida de graceful shutdown**:

- el runner usa `Stop-Process` sobre Java, no SIGTERM ni un mecanismo equivalente demostrado;
- no mide ni verifica que Spring haya completado sus fases de graceful shutdown;
- PostgreSQL y Redis se detienen en el `finally`, con `Stop-Process -Force` como fallback;
- no registra exit codes individuales ni un timeout de cada dependencia;
- no prueba PID 1 ni `docker stop`, limitación que sí está documentada.

Por este motivo el P0 de shutdown permanece abierto para la aprobación independiente. La evidencia prueba terminación/limpieza, no graceful shutdown.

## 17. Filesystem y logs locales

El smoke reportó `localLogArtifacts=0`. La inspección posterior confirmó:

- `app.log` del root inexistente;
- ningún archivo backend nuevo/modificado en `logs/`;
- ningún archivo backend nuevo/modificado en `ciberfootbolt_local/logs`;
- no se encontró un path Windows nuevo;
- no se observaron dumps o heap dumps generados por el JAR.

El directorio temporal del runner sí permanece en `%TEMP%` y contiene capturas de PostgreSQL/Redis/app y el data directory temporal. Esto es una fuga de limpieza del runner, no escritura del backend al filesystem de producción.

## 18. Tests backend

Se ejecutó:

- `mvn -q -DskipTests test-compile`: PASS;
- suite focal de logging/runtime/health/Redis/mapping/excepciones: PASS;
- `mvn -q test`: PASS.

Los reportes frescos de la ejecución completa fueron 292 y totalizaron:

- 2568 tests;
- 0 failures;
- 0 errors;
- 4 skipped.

No hubo suite roja. La suite respalda el cierre estático del artefacto, pero no convierte `Stop-Process` en una prueba de graceful shutdown.

## 19. Frontend

Se ejecutaron encoding, build development, build production, inspección del artefacto y ChromeHeadless:

- encoding: PASS, 385 archivos;
- build development: PASS;
- build production: PASS;
- inspección: PASS, 52 archivos;
- producción sin test harness, localhost bloqueante ni source maps: PASS;
- tests frontend: `1029 SUCCESS`, `0 failures`, `2 skipped`.

El build development muestra el lazy chunk del test harness, pero ese chunk no aparece en el artefacto production inspeccionado. No es un defecto del artifact productivo.

## 20. Documentación

La documentación nueva y actualizada preserva la auditoría histórica REJECTED, separa la remediación y declara honestamente que Docker build/run, tamaño de imagen, UID runtime, scan, deploy, restore y SSE cloud no fueron probados.

Los hashes, tamaño del JAR y conteos de suites coinciden con la evidencia actual, salvo que los reportes previos documentan el puerto `61012` y el nuevo smoke usó `61748`, diferencia esperable entre ejecuciones.

El punto que requiere corrección documental es el uso de “shutdown PASS”: la evidencia solo prueba terminación del proceso en Windows, no graceful shutdown. La documentación reconoce la limitación de Docker/PID 1, pero sobrecalifica el cierre como validación de shutdown.

## 21. P0 y P1

### P0 abierto

1. **Shutdown graceful no demostrado:** `Stop-Process` no es SIGTERM/graceful equivalente y la limpieza de dependencias usa force como fallback. El smoke PASS no permite cerrar este gate.

### P1

1. Docker build/run/inspect, healthcheck real, imagen, UID/capabilities, SBOM y vulnerability scan siguen bloqueados externamente.
2. El runner no limpia su `$work` temporal.
3. El runner puede declarar PASS si no encuentra IDs para la carrera mínima.
4. No hay segundo arranque del JAR contra la misma base temporal.
5. No se probaron runtime fallback `SERVER_PORT`, ausencia de variables de puerto ni puerto inválido.
6. El runner no registra startup duration, PIDs ni exit codes individuales.
7. El historial del commit `3b41073a` contiene trailing whitespace en el reporte anterior.
8. SSE detrás de proxy/cloud, routing real, backups, restore, CI/CD, rollback y observabilidad siguen siendo gates posteriores.

## 22. Preparado para Docker runner

**No aprobado todavía para avanzar como runtime cerrado.**

La imagen está lista para un Docker runner externo en cuanto exista una máquina con Docker, pero el P0 de shutdown debe corregirse o cubrirse con una prueba que emita una terminación graceful verificable antes de considerar cerrada la reauditoría.

## 23. Conclusión

La remediación técnica fue efectiva en logging portable, Jammy, curl, variables de binding, Redis SSL, contexto reducido, reproducibilidad, health, auth, carrera, Flyway y suites. El smoke real del JAR pasó con PostgreSQL y Redis temporales y no generó archivos locales del backend.

La decisión independiente sigue siendo **PB1.2.1 PRODUCTION RUNTIME REJECTED** porque la prueba de shutdown declarada como PASS no verifica graceful shutdown y el runner fuerza la limpieza de procesos. Docker es un bloqueo externo adicional, pero no es la razón del rechazo. Debe cerrarse el P0 de shutdown y repetirse esta auditoría antes de aprobar PB1.2.1.
