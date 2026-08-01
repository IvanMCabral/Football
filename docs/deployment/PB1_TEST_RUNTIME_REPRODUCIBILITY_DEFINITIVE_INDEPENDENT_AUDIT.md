# MANAGER  PB1.1 Definitive Reproducibility Final Independent Audit

**Fecha:** 2026-08-01
**Alcance:** reauditora independiente y de solo lectura del runtime de tests y de los bloqueantes PB1.1 declarados en `e9e26856`, `84d83eb5`, `0a1478ad` y `9c5482ff`.

## 1. Veredicto

# PB1.1 DEFINITIVE CLOSURE APPROVED WITH ISSUES

La evidencia independiente confirma dos suites backend completas consecutivas en verde, runtime efmero local para PostgreSQL y Redis, aislamiento real DB15/DB0, cleanup de health Redis sin falso `DOWN`, matriz de readiness y validacin frontend.

Quedan issues P1 de cobertura, documentacin y limpieza de archivos temporales. Ninguno de ellos constituye un P0 PB1.1 ni una fuga de informacin interna.

## 2. Commits auditados

- `e9e26856`  `Make integration test runtime self contained`.
- `84d83eb5`  `Fix Redis readiness cleanup semantics`.
- `0a1478ad`  `Complete PB1.1 reproducibility evidence`.
- `9c5482ff`  `Close PB1.1 hardening definitively`.

El rango revisado contiene bootstrap de PostgreSQL/Redis, health, errores, tests de aislamiento y documentacin. No contiene cambios de gameplay, simulacin productiva ni datasets productivos; los cambios relacionados con importer/baseline son de tests.

## 3. Git

- Root limpio antes del informe.
- `front-ciber/project` limpio.
- `git diff --check` del rango `8a34e1a6..9c5482ff`: limpio.
- `git diff --check` actual de root y frontend: limpio.
- No se hicieron commits, push ni modificaciones a cdigo, tests, configuracin o documentacin existente.
- No se observaron secretos nuevos en el rango auditado; las credenciales de los runtimes de test son aleatorias.

## 4. Alcance

Se verific exclusivamente:

1. autonoma real del runtime de integracin;
2. aislamiento PostgreSQL y Redis;
3. dos suites backend completas consecutivas;
4. cleanup de Redis health;
5. matriz readiness;
6. contrato HTTP 400/401/403/404/409/429/500;
7. ausencia de regresiones producidas por estos fixes.

Docker, Cloud Run, Firebase, Neon, Upstash productivo, backup/restore cloud, CI/CD, SSE detrs de proxy y headers edge/CDN permanecen fuera de alcance por corresponder a PB1.2.

## 5. Infraestructura del runtime de tests

| Componente | Binario | Puerto | Credenciales | Persistencia | Inicio | Cleanup |
|---|---|---|---|---|---|---|
| PostgreSQL | `initdb`, `postgres` | Aleatorio mediante `ServerSocket(0)` | Usuario `manager_test` y password aleatoria | Directorio temporal por proceso; `fsync`, `synchronous_commit` y `full_page_writes` desactivados | `PostgresTestEnvironmentPostProcessor` va `ProcessBuilder`; espera JDBC hasta 20 s | Shutdown hook; detiene proceso y borra recursivamente de forma best-effort |
| Redis | `redis-server` | Aleatorio mediante `ServerSocket(0)` | Password aleatoria; `requirepass` activo | `--save ""`, `--appendonly no` | `RedisTestEnvironmentPostProcessor` va `ProcessBuilder`; espera 500 ms y verifica proceso vivo | Shutdown hook; detiene proceso en hasta 3 s |

Las propiedades de ambos runtimes se inyectan en primer lugar del `Environment`. PostgreSQL usa JDBC, R2DBC y Flyway sobre la misma instancia efmera; Redis usa Spring Redis DB 15.

## 6. Requisitos externos reales

La ejecucin requiere binarios instalados, no servicios previamente iniciados ni secretos:

- Java 21  verificado `21.0.8`;
- Maven  verificado `3.9.11`;
- PostgreSQL `initdb` y `postgres`  verificados `18.1`;
- `redis-server`  verificado `8.8.0`;
- Node/npm y ChromeHeadless para la validacin frontend.

Los binarios se resuelven por `PATH`. Si falta un binario, el bootstrap falla con un mensaje que indica instalarlo o desactivar el bootstrap y proveer propiedades aisladas. No hay resolucin automtica de rutas alternativas de Windows ni versiones mnimas formalmente documentadas; esto es un issue P1 de portabilidad/CI, no un bloqueo PB1.1.

La estrategia es autnoma respecto de servicios, pero no respecto de binarios instalados. Es conceptualmente portable a Linux CI porque usa nombres de ejecutables y `PATH`; la compatibilidad efectiva de versiones y paquetes queda para PB1.2/CI.

## 7. Clean checkout

La ejecucin independiente elimin las variables de proceso DB/Redis/Spring/Manager y no carg `.env`. El suite no usa la password del desarrollador ni el nombre `football_manager`.

El entorno de la mquina tena procesos PostgreSQL previos antes de las corridas. Sus mismos PIDs permanecieron antes y despus; no fueron iniciados por esta auditora. Las pruebas no dependieron de ellos: los logs del bootstrap muestran instancias separadas en puertos aleatorios `64272` y `53249`, mientras el servicio local qued fuera del puerto elegido.

No haba procesos `redis-server` antes ni despus. No quedaron procesos Java de las suites.

Resultado: **PASS en autonoma de configuracin y servicios requeridos; PARTIAL en simulacin de una mquina fsicamente sin PostgreSQL residente**, porque no se detuvieron procesos preexistentes en una auditora de solo lectura.

## 8. PostgreSQL runtime

`PostgresTestEnvironmentPostProcessor`:

- crea un directorio temporal nuevo;
- ejecuta `initdb` con usuario y password efmeros;
- agrega autenticacin `scram-sha-256`;
- configura bind `127.0.0.1` y puerto aleatorio;
- crea la base `postgres` dentro de esa instancia efmera;
- inyecta las URLs JDBC, R2DBC y Flyway;
- espera una conexin JDBC real antes de devolver el contexto;
- registra el proceso en un shutdown hook.

`TestRuntimeIsolationIntegrationTest` confirma mediante el bean efectivo que Flyway aplic migraciones y que R2DBC consulta la base `postgres` del runtime efmero. No se usa la base de negocio local.

Observacin: el teardown detiene los procesos, pero deja archivos `postgres.log` dentro de directorios temporales. En la mquina quedaron 17 directorios histricos de este patrn, incluidos tres generados durante las validaciones actuales. No qued el `data` directory completo ni un proceso de esas instancias. Es un issue P1 de cleanup.

## 9. PostgreSQL isolation

La prueba real verifica:

- `flyway_schema_history` con filas en la instancia levantada;
- `current_database() = postgres` mediante R2DBC;
- JDBC, R2DBC y Flyway apuntando al mismo puerto/instancia efmera;
- puertos diferentes entre las corridas observadas;
- ausencia de proceso efmero despus de cada corrida.

Esto demuestra aislamiento operativo frente a la base local. No existe un test automatizado que escriba una sentinel, destruya la instancia, levante otra y compruebe explcitamente que la sentinel desapareci, ni una consulta de auditora contra `football_manager` local. La arquitectura de directorio y puerto aleatorios lo hace plausible y las corridas lo ejercitan, pero la sentinel cross-run queda como P1 de evidencia.

## 10. Redis runtime

`RedisTestEnvironmentPostProcessor`:

- selecciona un puerto aleatorio;
- genera password aleatoria de 24 bytes;
- hace bind a `127.0.0.1`;
- activa `requirepass` y protected mode;
- inicia 16 bases lgicas;
- configura DB 15 para Spring test;
- deshabilita snapshots y AOF;
- no usa `.env`, puerto fijo ni Redis local;
- instala shutdown hook de hasta 3 segundos.

El arranque verifica que el proceso siga vivo despus de 500 ms, pero no ejecuta un `PING` autenticado antes de exponer el runtime al contexto. La conexin real queda validada al iniciar la aplicacin y durante los tests; agregar una readiness autenticada al bootstrap sera una mejora P1.

## 11. Redis DB15/DB0

`TestRuntimeIsolationIntegrationTest` usa el bean efectivo de Spring para DB 15 y crea un segundo `LettuceConnectionFactory` autenticado contra el mismo Redis para DB 0. La prueba:

1. escribe sentinel A en DB 0;
2. escribe sentinel B en DB 15;
3. lee A nicamente en DB 0;
4. lee B nicamente en DB 15;
5. comprueba ausencia cruzada en ambos sentidos;
6. elimina ambas sentinels;
7. comprueba que ambas desaparecieron.

Resultado: **PASS con integracin real**, no solo inspeccin de `RedisStandaloneConfiguration`.

## 12. Redis isolation entre corridas

Las dos suites completas iniciaron JVMs independientes con puertos y passwords efmeros. No haba procesos Redis previos ni posteriores, no se observaron puertos ocupados y Redis no dej archivos persistentes.

Dentro de la JVM, `AbstractIntegrationTest` ejecuta `flushDb()` sobre DB 15 antes de cada test de integracin. La prueba DB15/DB0 elimina sus sentinels explcitamente.

No existe un test de ejecucin paralela de dos suites, abortado a mitad de arranque o contaminacin entre procesos concurrentes. La evidencia de dos corridas consecutivas es suficiente para el cierre P0, pero la cobertura concurrente queda como P1.

## 13. Redis health cleanup

El probe ejecuta `SET` con TTL de 5 segundos, `GET`, comparacin exacta con `ok` y `DELETE` sobre una key aleatoria.

Contrato verificado:

- `SET` falla: `DOWN`;
- `GET` falla: `DOWN`;
- valor incorrecto: `DOWN`;
- `SET + GET` correctos y `DELETE` correcto: `UP`;
- `SET + GET` correctos y `DELETE` fallido: `UP`;
- cleanup fallido: warning interno sin password, connection string, secreto ni payload;
- TTL de 5 segundos como limpieza eventual;
- excepcin del driver no expuesta al cliente.

`RedisHealthProbeCleanupTest` ejecuta el caso DELETE fallido y pasa. El warning solo incluye el prefijo fijo de la key.

Resultado: **PASS**; el falso `DOWN` que bloqueaba la auditora anterior est cerrado.

## 14. Redis health matrix

| Caso | Tipo de cobertura | Resultado |
|---|---|---|
| Redis vaco y operativo | Integracin real (`RedisHealthProbeIntegrationTest`) | **PASS** |
| Valor incorrecto | Unitario reactivo | **PASS** |
| DELETE falla despus de SET/GET | Unitario reactivo | **PASS**, mantiene `UP` |
| SET falla | No hay test especfico | **UNVERIFIED**, lgica fail-closed |
| GET falla | No hay test especfico | **UNVERIFIED**, lgica fail-closed |
| Redis apagado | No hay integracin controlada | **UNVERIFIED**, lgica fail-closed |
| Puerto incorrecto | No hay test | **UNVERIFIED** |
| Password incorrecta | No hay test | **UNVERIFIED** |
| Timeout | No hay test controlado | **UNVERIFIED** |
| TTL/cleanup efectivo | Integracin real y TTL configurado | **PARTIAL** |

La lgica crtica queda cubierta de forma suficiente para no abrir un P0, pero la matriz operacional completa es P1.

## 15. Readiness matrix

`HealthControllerReadinessMatrixTest` verifica las cuatro combinaciones en el controlador:

| PostgreSQL | Redis | HTTP | Body |
|---|---|---:|---|
| UP | UP | 200 | `status=UP`, `database=UP`, `redis=UP` |
| DOWN | UP | 503 | `status=DOWN`, `database=DOWN`, `redis=UP` |
| UP | DOWN | 503 | `status=DOWN`, `database=UP`, `redis=DOWN` |
| DOWN | DOWN | 503 | `status=DOWN`, `database=DOWN`, `redis=DOWN` |

Liveness est cubierto y responde `200` con `status=UP` sin depender de DB/Redis. Los timeouts de ambos probes estn acotados a 2 segundos. El body de health no incluye host, password ni causa interna.

La matriz es unitaria del controlador, no una prueba HTTP end-to-end con conexiones reales cadas. Por eso se marca **PASS de lgica / PARTIAL de integracin HTTP**.

## 16. HTTP error contract

El contrato central es `code`, `message`, `status` y `requestId`. Los tests productivos de `GlobalExceptionHandler` cubren mensajes sensibles para validacin, 401, 403, 404, 409 y 500; el rate limit cubre 429. No se observ exposicin de URL interna, clase de driver, ruta local, SQL, password ni mensaje arbitrario en esos handlers.

Quedan inconsistencias no sensibles:

- el access denied del `SecurityConfig` devuelve 403 vaco, aunque el `GlobalExceptionHandler` tiene un body seguro para `AccessDeniedException`;
- varios controllers devuelven `Map.of("error", ...)`, bodies vacos o DTOs especficos en validaciones inline, sin el contrato uniforme completo;
- `IllegalArgumentException` del handler central produce 422, mientras ciertas rutas inline producen 400;
- la evidencia principal es de handlers/unit tests y de la suite E2E general, no una matriz HTTP dedicada para cada combinacin real 400/401/403/404/409/500 con DB/Redis cado.

Clasificacin: **sin fuga crtica; inconsistencia de contrato P1**.

## 17. Backend run 1

Condiciones observadas:

- variables DB/Redis/Spring/Manager retiradas del proceso;
- `.env` no cargado;
- Redis no iniciado manualmente;
- bootstrap PostgreSQL/Redis automtico;
- Maven `test` completo.

Resultado de los 291 XML modificados dentro de la ventana de la corrida:

```text
2564 tests
0 failures
0 errors
4 skipped
duracin: 246.0 segundos
exit code: 0
```

Los conteos se calcularon solo con XML con timestamp entre el inicio y el final de esta corrida. Maven no respet el override de `surefire.reportsDirectory`; no se mezclaron XML anteriores en el conteo.

## 18. Backend run 2

Se ejecut inmediatamente despus, sin cargar `.env`, sin exportar variables, sin reiniciar manualmente DB/Redis y sin matar procesos entre corridas.

Resultado de los 291 XML modificados dentro de la segunda ventana:

```text
2564 tests
0 failures
0 errors
4 skipped
duracin: 246.3 segundos
exit code: 0
```

La segunda corrida confirm que el bootstrap no depende de keys Redis ni datos PostgreSQL de la primera. No hubo errores de puerto ocupado ni procesos Redis hurfanos.

## 19. Timeout y rendimiento

Las duraciones independientes fueron `246.0 s` y `246.3 s`, con diferencia de `0.3 s` y variacin aproximada del `0.1%` entre corridas. Son aproximadamente 7.3 segundos ms lentas que los 238.7 segundos declarados, pero permanecen debajo de cinco minutos y son estables localmente.

No se observ una espera fija que impida la ejecucin; el costo principal proviene de levantar PostgreSQL/Flyway y la suite E2E. La viabilidad en CI Linux depende de proveer binarios y recursos equivalentes, issue PB1.2/CI.

## 20. Frontend

Validacin ejecutada en `front-ciber/project`:

- encoding guard: **PASS**, 385 archivos;
- development build: **PASS**;
- production build: **PASS**;
- ChromeHeadless: **PASS**, `1029 SUCCESS`, `0 failures`, `2 skipped`;
- inspeccin de `dist/demo`: sin referencias `test-harness`, `test harness`, `debug/test` ni `testharness`.

El build de desarrollo s contiene el chunk de test harness, como corresponde. El build de produccin no lo incluye. Las coincidencias genricas de la palabra `debug` en libreras Angular no corresponden al harness ni a una ruta de debug del proyecto.

## 21. Redis durability

La documentacin sigue declarando honestamente que:

- Redis es runtime-critical;
- el Redis de tests es efmero y no representa produccin;
- health no equivale a backup;
- restore, backup gestionado y loss/reconnect no fueron probados;
- proveedor Redis gestionado y TTL productivos quedan pendientes;
- PB1.2 mantiene abiertos los drills de durabilidad y recuperacin.

Estos gates no se usan para rechazar PB1.1.

## 22. Documentacin

Los documentos de runtime (`PB1_TEST_RUNTIME_REPRODUCIBILITY_REMEDIATION.md` y `PB1_TEST_RUNTIME_REPRODUCIBILITY_FINAL_REVIEW.md`) describen el bootstrap local, la dependencia de binarios, DB15/DB0, cleanup, readiness y los resultados `2564/0/0/4`.

Las auditoras histricas rechazadas permanecen preservadas. Sin embargo, varios documentos de cierre anteriores todava contienen el resultado antiguo `2553/0/0/4`, mientras los documentos definitivos declaran `2564/0/0/4`. Tambin faltan versiones mnimas y pasos de instalacin por sistema operativo para los binarios. Esto es una inconsistencia documental P1, no evidencia de una suite fallida.

## 23. P0 PB1.1

### P0 cerrados

- Suite backend no reproducible: **CERRADO**, dos corridas completas en verde sin `.env` ni servicios manuales requeridos.
- Fuga de mensajes internos: **CERRADO**, handlers productivos y suite completa sin exposicin confirmada.
- Health Redis falso `DOWN` por cleanup: **CERRADO**, cleanup best-effort probado.
- Aislamiento DB15/DB0: **CERRADO**, prueba real con el bean efectivo.

### P0 abiertos

**Ninguno identificado.**

## 24. P1 PB1.1

- Cleanup PostgreSQL deja `postgres.log` en directorios temporales despus del shutdown hook.
- La matriz Redis de puerto, password, timeout y fallos individuales SET/GET no est cubierta con tests dedicados.
- Readiness est probado como lgica unitaria, pero falta matriz HTTP end-to-end con DB/Redis realmente cados.
- El contrato HTTP no es uniforme en 403 del security handler y en varias respuestas inline, aunque no se encontr fuga interna.
- Falta sentinel automatizada cross-run PostgreSQL y verificacin explcita contra la DB local principal.
- Falta prueba de concurrencia/abort/restart para aislamiento Redis.
- Requisitos de binarios no declaran versiones mnimas ni instalacin Windows/Linux.
- Documentos anteriores mantienen nmeros `2553` incompatibles con la evidencia definitiva `2564`.
- La carpeta de reportes histrica no fue fsicamente eliminada por la auditora de solo lectura; se excluy por timestamps y solo se contaron XML frescos.

## 25. PB1.2 gates

Fuera del rechazo PB1.1:

- Docker/buildpack;
- Cloud Run/Firebase;
- Neon y Upstash gestionados;
- CI/CD;
- backups y restore cloud;
- Redis gestionado persistente;
- loss/reconnect drill;
- SSE detrs de proxy;
- headers edge/CDN;
- Internet-ready deployment.

## 26. Readiness

**S, preparado para iniciar PB1.2**, con los P1 anteriores registrados y sin declarar que el sistema sea todava Internet-ready. La aprobacin corresponde al cierre definitivo de reproducibilidad PB1.1, no al cierre de infraestructura productiva cloud.

## 27. Conclusin

La implementacin actual elimina el bloqueo de reproducibilidad que exista en la auditora anterior. PostgreSQL y Redis se levantan automticamente con procesos efmeros, puertos y credenciales aleatorios; Flyway y R2DBC usan la misma base aislada; Redis DB15 y DB0 no comparten sentinels; health cleanup no produce falso `DOWN`; readiness distingue las cuatro combinaciones; y las dos suites backend completas pasan con el mismo conteo.

La aprobacin es con issues por cleanup incompleto de logs temporales, cobertura operacional an no exhaustiva, contrato HTTP no totalmente uniforme y documentacin histrica con conteos incompatibles. Ninguno de esos hallazgos reabre un P0 de PB1.1.

**PB1.1 DEFINITIVE CLOSURE APPROVED WITH ISSUES**
