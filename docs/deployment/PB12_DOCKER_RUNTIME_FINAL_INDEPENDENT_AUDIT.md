# PB1.2.2 Docker Runtime Final Independent Audit

Fecha: 2026-08-02

## 1. Veredicto

**PB1.2.2 DOCKER RUNTIME APPROVED WITH ISSUES**

El run remoto real de GitHub Actions fue verificable y publicó `PASS`, sin fallas P0 del runtime Docker. El gate Docker queda aprobado con seguimiento P1 obligatorio por tres vulnerabilidades HIGH con versión corregida disponible, por higiene incompleta de temporales del runner, por evidencia del ZIP que no pudo descargarse desde esta estación y por documentación de conteos backend desactualizada. Esto no equivale a aprobación cloud ni a aprobación para exposición pública.

Fuente primaria del run: [GitHub Actions run #22](https://github.com/IvanMCabral/Football/actions/runs/30755616038).

## 2. Commits auditados

Se auditaron explícitamente:

| Commit | Mensaje | Resultado |
|---|---|---|
| `f92f2eee` | Verify production smoke summary cleanup | PASS dentro de su alcance |
| `430f50bf` | Recover Java when smoke helper exits | PASS dentro de su alcance |
| `31aadbe5` | Close PB1.2.1 local runtime evidence | PASS dentro de su alcance |

El árbol actual contiene además la implementación Docker posterior que ejecutó el run #22. No se atribuye esa implementación a los tres commits anteriores; se inspeccionó el estado actual para verificar el gate Docker solicitado.

## 3. Git

- Rama: `feat/v25d99.20.3.1-runtime-fixes`.
- HEAD local y rama remota: `442e5caa6a46b7fb3d195e3d2f788b3c68b80a6d`.
- Los tres commits auditados están presentes.
- Estado Git root: limpio.
- Estado Git frontend `front-ciber/project`: limpio.
- `git diff --check` actual: limpio.
- El rango histórico `31aadbe5..HEAD` conserva un trailing whitespace en una documentación histórica ya versionada (`PB12_FINAL_LOCAL_RUNTIME_EVIDENCE_INDEPENDENT_AUDIT.md`, línea 3); no se modificó.
- Los cambios Docker observados están limitados a workflow, runner, Dockerfile, configuración de dependencias, guard test y documentación. No se observan cambios de gameplay, simulación, datasets ni secretos nuevos.

## 4. Workflow verification

`.github/workflows/pb12-docker-smoke.yml` ejecuta en `ubuntu-24.04`, con timeout de 35 minutos, checkout del revision, credenciales efímeras enmascaradas, PostgreSQL y Redis descartables, build real `docker build --pull --no-cache`, smoke de ciclo de vida, SBOM CycloneDX, Trivy y artifact de siete días.

El workflow falla ante error del runner, ausencia de result JSON, ausencia de SBOM/scan o vulnerabilidades CRITICAL. Los HIGH se cuentan y se publican como warning. No usa `docker system prune`, no publica la imagen y no declara despliegue cloud.

Limitaciones P1: las acciones de terceros están referenciadas por tag, no por SHA inmutable; el base image tampoco está fijado por digest.

## 5. GitHub Actions run

- Run: [#22, Record Docker smoke login evidence](https://github.com/IvanMCabral/Football/actions/runs/30755616038).
- Run ID: `30755616038`.
- Estado remoto: `Success`.
- Duración: `2m19s`; job `2m17s`.
- Head SHA del run: `3ee8d2a4`.
- Branch: `feat/v25d99.20.3.1-runtime-fixes`.
- El run fue disparado por push y tiene un artifact.

El SHA del run no es el HEAD local final auditado (`442e5caa`); por eso el resultado remoto se toma como evidencia del revision que realmente ejecutó GitHub, mientras que el workflow y el runner actuales se auditan estáticamente. Esta diferencia debe cerrarse con un run posterior sobre el HEAD final antes de un release público.

## 6. Remote conclusion

El run remoto es real y verificable mediante la página pública, su estado `Success`, el result summary publicado y la metadata del artifact. El archivo ZIP no pudo descargarse desde esta estación: `gh` no está disponible y el endpoint público de descarga requiere autenticación. Por lo tanto, no se inventa una inspección local de los contenidos; se conserva esta limitación como P1 de reproducibilidad de evidencia, no como un P0 del runtime.

## 7. Artifacts downloaded

No descargado localmente. La página remota confirma:

- nombre: `pb12-docker-smoke-30755616038`;
- tamaño: `88.2 KB`;
- digest: `sha256:23cfd415d8a0521d5cbf04b64b48ec02ead4e40050610e404dab789ef3b52997`.

El artifact esperado contiene logs sanitizados, inspect/history, result JSON, SBOM, Trivy y matriz de readiness según el workflow versionado.

## 8. Docker build e imagen

- Docker local: no disponible; no se construyó localmente.
- Build remoto: PASS.
- Image ID: `sha256:f5e3853cfecd62f78967578efd1dbe88aacb1401cf453a789d5a406c9849b372`.
- Tamaño: `258908085` bytes.
- Base runtime: `eclipse-temurin:21.0.11_10-jre-alpine-3.23`.
- Arquitectura reportada: la imagen fue inspeccionada por el runner; el summary público no la expone.
- Usuario: `manager`.
- UID: `122`.
- Imagen read-only en el smoke y con `no-new-privileges`.

El Dockerfile usa multi-stage Maven, `curl`, healthcheck, `USER manager` y no contiene FileAppender ni secretos. El tag de base sin digest es P1 de supply chain.

## 9. Runtime user, Java PID 1 y healthcheck

- Usuario runtime: `manager` / UID `122`: PASS.
- Java PID 1: PASS; el entrypoint usa `exec java` y el runner verifica `/proc/1/cmdline`.
- Docker HEALTHCHECK: saludable: PASS.
- Liveness: `200`: PASS.
- Readiness: `200`: PASS.
- `SERVER_ADDRESS=0.0.0.0`: configurado y ejercitado.

## 10. Auth, career y Flyway

- Register: PASS.
- Login: PASS.
- `/me`: PASS.
- Career creada: PASS.
- Career recuperada después del restart: PASS.
- Flyway run 1: `1` migración exitosa.
- Flyway run 2: `1` migración exitosa.
- Segundo startup: PASS.
- La misma PostgreSQL temporal se mantiene entre ambos arranques; no se observa reinicialización destructiva.

## 11. Docker stop y graceful shutdown

- `docker stop`: usado: `true`.
- `docker kill`: usado: `false`.
- Graceful shutdown observado: `true`.
- Orden de markers: válido: `true`.
- Markers concretos inspeccionados por el runner: `Commencing graceful shutdown` seguido de `Graceful shutdown complete`.
- Duración: `2505 ms`.
- Exit code: `143`, coherente con SIGTERM de `docker stop` y distinto de un force kill.

El runner no usa `docker kill` como camino normal. El único `docker rm -f` está reservado para cleanup posterior cuando un container falló o no terminó, marca `cleanupForceUsed=true` y vuelve inválido el PASS. En el run declarado `dockerKillUsed=false`, no hay evidencia de force kill y el contenedor terminó mediante `docker stop`.

## 12. Redis-down matrix

El runner detiene Redis, exige liveness `200` y readiness `503`, registra la respuesta, reinicia Redis y espera su healthcheck. El run remoto publicó readiness `503`: PASS.

## 13. PostgreSQL-down matrix

El runner detiene PostgreSQL, exige liveness `200` y readiness `503`, registra la respuesta, y valida la matriz antes del cleanup. El run remoto publicó readiness `503`: PASS.

## 14. Filesystem, logs y secretos

- Runtime read-only: configurado.
- `docker diff` del backend de restart: `0` escrituras inesperadas, excluyendo `/tmp` temporal.
- Inspección estática: rechaza root, `.env`, logs y patrones de secretos en `/app`.
- Logback: solamente `ConsoleAppender`; no hay FileAppender.
- El runner genera DB/Redis/JWT efímeros, los enmascara y no los copia al artifact.

P1 de evidencia: `AUTH_TMP` se crea con respuestas que incluyen token y no se elimina explícitamente en `cleanup()`. Además, el grep de secretos se ejecuta antes de que el `EXIT` trap capture logs de containers; esos logs de cleanup no quedan cubiertos por esa comprobación. El artifact del run no mostró leak en el summary, pero el cleanup/hygiene no es completamente fail-closed.

## 15. Cleanup

- Cleanup de backend run 1, backend run 2, PostgreSQL, Redis y network: implementado.
- Residual containers: `0`.
- Residual networks: `0`.
- `cleanupVerified`: `true`.
- `docker system prune`: no usado.
- Force cleanup en el run PASS: no observado; `cleanupForceUsed` queda falso según el resultado remoto.

La verificación remota cubre containers y network. No cubre explícitamente la eliminación de `AUTH_TMP`, por lo que el cleanup se considera aprobado para el runtime Docker pero con P1 de higiene del runner.

## 16. SBOM y Trivy

- SBOM: generado en formato CycloneDX JSON por Anchore.
- Trivy: ejecutado sobre OS y library packages, con `CRITICAL,HIGH` y `ignore-unfixed=true`.
- CRITICAL: `0`.
- HIGH: `3`.
- El workflow falla si falta el SBOM o el scan, y falla si hay CRITICAL.

La política del repositorio deja los HIGH visibles como seguimiento P1; no los oculta ni los convierte en PASS silencioso.

## 17. Clasificación CVE 1 — CVE-2026-41695

Paquete observado: `org.springframework.data:spring-data-commons:3.5.11`; correcciones reportadas: `3.5.12` y `4.0.6`. Fuente: [Spring advisory CVE-2026-41695](https://spring.io/security/cve-2026-41695/).

Clasificación: **ACCEPTED TEMPORARILY — P1**. Es una dependencia runtime real y tiene fix disponible, por lo que no es build-only ni false positive. La aplicación no usa Spring Data REST ni expone a callers no confiables una API genérica de property path; el exploit depende de esa condición. Debe actualizarse y documentarse owner/deadline antes de beta pública.

## 18. Clasificación CVE 2 — CVE-2026-41850

Paquete observado: `org.springframework:spring-expression:6.2.18`; correcciones reportadas: `6.2.19` y `7.0.8`. Fuente: [Spring advisory CVE-2026-41850](https://spring.io/security/cve-2026-41850/).

Clasificación: **ACCEPTED TEMPORARILY — P1**. Es una dependencia runtime real y tiene fix disponible, pero el código auditado no evalúa SpEL suministrado por usuarios ni expone un parser de expresiones controlado externamente. Debe actualizarse y quedar con owner/deadline antes de beta pública.

## 19. Clasificación CVE 3 — CVE-2026-41842

Paquete observado: `org.springframework:spring-webflux:6.2.18`; correcciones reportadas: `6.2.19` y `7.0.8`. Fuente: [Spring advisory CVE-2026-41842](https://spring.io/security/cve-2026-41842/).

Clasificación: **ACCEPTED TEMPORARILY — P1**. Es una dependencia runtime real y tiene fix disponible, pero el backend no sirve recursos estáticos versionados desde filesystem en el flujo productivo auditado; los recursos revisados son classpath data. Debe actualizarse y quedar con owner/deadline antes de beta pública.

## 20. Prevención de falso PASS

La evidencia está protegida por checks ejecutables para build, imagen, usuario, healthcheck, liveness/readiness, auth, career, Flyway, restart, PID 1, docker stop, markers, matriz de dependencias, filesystem, scan y cleanup. Una falla del runner propaga exit code no cero; la ausencia de result, SBOM o scan falla el workflow; CRITICAL mayor que cero falla el gate.

| Condición | Resultado esperado | Auditoría |
|---|---|---|
| Build falla | FAIL | Implementado |
| Health/readiness falla | FAIL | Implementado |
| Career no creada | FAIL | Implementado |
| Segundo startup falla | FAIL | Implementado |
| Flyway cambia o no es positivo | FAIL | Implementado |
| Marker Spring ausente o fuera de orden | FAIL | Implementado |
| Java no es PID 1 | FAIL | Implementado |
| `docker stop` falla | FAIL | Implementado |
| `docker kill` / force path en PASS | FAIL | No hay `docker kill`; cleanup force se marca y revoca PASS |
| Redis/PostgreSQL readiness incorrecta | FAIL | Implementado |
| Escrituras inesperadas | FAIL | Implementado |
| Secret pattern en evidencia previa al cleanup | FAIL | Implementado, con gap P1 para logs del EXIT trap |
| SBOM/Trivy ausente | FAIL | Implementado |
| CRITICAL > 0 | FAIL | Implementado |
| Container/network residual | FAIL | Implementado |

Observación P1: `dockerKillUsed` nace en `false` y no se deriva de un detector dinámico; la ausencia de la operación `docker kill` está comprobada estáticamente en el runner. No constituye evidencia de un force kill oculto en el run #22.

## 21. Persistencia y lifecycle

El run 1 crea usuario y career sobre la DB temporal, detiene solamente el backend con `docker stop`, y el run 2 levanta otro proceso en otro puerto contra la misma DB y Redis. Login, consulta de games y Flyway `1/1` prueban persistencia mínima y no reinicialización destructiva. No se exige restore Redis ni backup/restore cloud en PB1.2.2.

No hay SSE ni request activa durante el shutdown; queda como P1/PB1.2.3, no como P0 del shutdown básico.

## 22. Backend tests

Evidencia local fresca sobre el HEAD actual:

- `mvn -q -DskipTests test-compile`: PASS.
- `mvn -q test`: PASS.
- Reports frescos: `2778` tests, `0` failures, `0` errors, `4` skipped.

El conteo esperado en documentación previa era `2571`; la diferencia es `+207` tests frente a esa documentación. No hay regresión funcional —todo sigue verde—, pero los documentos que continúan declarando `2571` están desactualizados y requieren corrección P1.

## 23. Frontend tests

- Encoding guard: PASS, `385` archivos inspeccionados.
- Development build: PASS.
- Production build: PASS.
- ChromeHeadless: `1029 SUCCESS`, `0` failures, `2` skipped.
- Artifact de producción: generado; el smoke Docker no sirve como harness frontend.

## 24. Documentation consistency

La documentación Docker describe correctamente build remoto, usuario no-root, PID 1, health, auth/career, Flyway, restart, docker stop, readiness negativa, SBOM/Trivy y límites cloud. También conserva el histórico PB1.2.1 rechazado/bloqueado sin declararlo como Docker probado.

Inconsistencias a corregir como P1:

- `PB12_DOCKER_RUNTIME_FINAL_REVIEW.md`, `PB12_DOCKER_RUNTIME_IMPLEMENTATION_REPORT.md`, `PB12_DOCKER_SMOKE_EXECUTION_REPORT.md` y varios documentos PB1.2.1 declaran `2571` tests; el reporte fresco es `2778`.
- Los documentos hablan de artifact sanitizado, pero esta estación no pudo extraerlo para verificar cada archivo.
- El run #22 corresponde a `3ee8d2a4`, no al HEAD final `442e5caa`.
- Los tres HIGH tienen fixes publicados pero no owner/deadline versionado.

## 25. P0 PB1.2.2

No se encontró P0 del runtime Docker en la evidencia remota publicada:

- build PASS;
- runtime no-root PASS;
- Java PID 1 PASS;
- health/liveness/readiness PASS;
- auth/career PASS;
- Flyway y segundo startup PASS;
- docker stop graceful PASS;
- no docker kill en PASS;
- markers y exit code compatibles PASS;
- matrices Redis/PostgreSQL PASS;
- CRITICAL `0`;
- residual containers/networks `0`;
- cleanup reportado PASS.

Los HIGH presentes, la falta de descarga del artifact, la limpieza de `AUTH_TMP`, el conteo stale y la falta de pinning son P1, no P0 del runtime probado.

## 26. P1 PB1.2.2

1. Actualizar los tres paquetes Spring con fixes disponibles y documentar owner/deadline.
2. Eliminar explícitamente `AUTH_TMP` y ejecutar el secret scan después de capturar logs de cleanup; hacer que `cleanupVerified` incluya ambos controles.
3. Ejecutar el mismo workflow sobre el HEAD final `442e5caa` y conservar el artifact descargable para la auditoría.
4. Actualizar conteos backend documentales de `2571` a `2778` donde correspondan.
5. Fijar por SHA las actions de terceros y por digest el base image antes de un pipeline de release.
6. Añadir una prueba de request/stream activo durante shutdown en PB1.2.3 si el contrato cloud lo requiere.

## 27. Readiness y preparación para PB1.2.3

PB1.2.2 está preparado para pasar al trabajo PB1.2.3 de infraestructura/cloud, pero no está listo para afirmar staging público. Permanecen fuera de este gate: Cloud Run/Firebase, dominios y CORS reales, PostgreSQL/Redis administrados, TLS/persistencia, backup/restore, RPO/RTO, CI/CD de deploy, proxy SSE y validación `docker stop` en el runtime cloud elegido.

## Conclusión

El runtime Docker probado en GitHub Actions cumple los gates funcionales y de lifecycle de PB1.2.2 y no presenta P0. El veredicto correcto es **PB1.2.2 DOCKER RUNTIME APPROVED WITH ISSUES**: aprobado para continuar al gate PB1.2.3, con los P1 enumerados obligatorios antes de beta/exposición pública.
