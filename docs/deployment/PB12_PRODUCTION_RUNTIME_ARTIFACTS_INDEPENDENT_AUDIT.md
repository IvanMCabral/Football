# PB1.2.1 — Auditoría independiente de artefactos/runtime productivos

Fecha de auditoría: 2026-08-01  
Alcance: artefacto backend productivo, Dockerfile, contexto de imagen, configuración runtime, JAR, frontend hosting artifact y evidencia local disponible.  
Modo: auditoría de solo lectura. No se modificó código de producto, configuración existente ni documentación previa.

## 1. Veredicto

**PB1.2.1 PRODUCTION RUNTIME REJECTED**

El artefacto no está aprobado para producción. La causa principal es bloqueante: el `logback.xml` empaquetado dentro del JAR fuerza un `RollingFileAppender` hacia una ruta absoluta de Windows (`D:/ProyectosOpenCode/MANAGER/ciberfootbolt_local/logs/...`) y mantiene el root logger en `DEBUG`. Esto contradice el runtime Linux Alpine, el usuario no root, la política documentada de stdout/stderr y el contrato de filesystem efímero.

Además, el `HEALTHCHECK` depende de `wget` sin instalación explícita ni verificación posible en la imagen porque Docker no está disponible; el JAR no completó un smoke de arranque productivo aislado; y dos builds limpios produjeron hashes diferentes. Estas condiciones deben cerrarse antes de cualquier aprobación.

## 2. Commits auditados

### Root

- `2908ee3f Add production backend container artifact`
- `ba576767 Validate production runtime contract`
- `41a55161 Close PB1.2.1 runtime artifact`

### Frontend

- `93b2763 Prepare frontend hosting artifact`

## 3. Estado Git

El root y `front-ciber/project` estaban limpios al inicio de la auditoría. La única modificación producida por esta auditoría es este reporte independiente.

La comparación `git diff --check 9c5482ff..41a55161` no mostró errores de whitespace. El rango de commits auditado se mantiene acotado al artefacto de contenedor, validaciones y documentación de runtime; no se observaron cambios de gameplay, simulación o datasets dentro de ese rango.

## 4. Dockerfile

El Dockerfile usa un build multi-stage:

- build: `maven:3.9.11-eclipse-temurin-21`;
- runtime: `eclipse-temurin:21.0.8_9-jre-alpine`;
- copia explícita de `pom.xml` y `src` al stage de build;
- empaquetado con `mvn -q -DskipTests package`;
- copia del JAR como `/app/app.jar` con ownership `manager:manager`;
- `WORKDIR /app`, `USER manager`, `EXPOSE 8080`;
- `ENTRYPOINT` con `exec java ... -jar /app/app.jar`.

La estructura es razonable estáticamente, con tags explícitos y separación build/runtime. No obstante, el artefacto no puede aprobarse por el logger incompatible y por dependencias del healthcheck no verificadas en imagen.

## 5. Compatibilidad Alpine

La base runtime declarada es Temurin JRE 21 sobre Alpine. No se pudo construir ni inspeccionar la imagen: Docker no está instalado/disponible en el entorno de auditoría.

Quedan sin evidencia de imagen:

- disponibilidad real de `wget`;
- certificados CA y resolución DNS;
- comportamiento de timezone;
- librerías nativas y compatibilidad musl;
- UID/GID efectivo y permisos finales;
- ejecución real del JAR sobre Alpine.

La compatibilidad Alpine queda **no demostrada**. La alternativa operativa más conservadora es usar una base Temurin JRE Debian/Ubuntu, o demostrar explícitamente todas las dependencias necesarias en Alpine.

## 6. `.dockerignore` y contexto

El `.dockerignore` excluye `.git`, `.env`, dumps, RDB, logs, directorios de dependencias y builds frontend, archivos de IDE, screenshots/media, temporales, `secrets/` y extensiones de claves/certificados.

La protección contra inclusión accidental de secretos es adecuada para los patrones observados. Sin embargo, el contexto todavía puede incluir numerosos documentos y archivos raíz rastreados; no se excluyen de forma general `docs/`, `README` u otros documentos. El Dockerfile actual no los copia al runtime final, pero el contexto de build sigue siendo más amplio de lo necesario. Esto queda como P1 de higiene y superficie de build.

## 7. Secretos y contenido del JAR/imagen

La inspección de las entradas del JAR no encontró `.env`, `.pem`, `.key`, `.p12`, `.jks`, `.dump`, `.rdb`, `.log` ni archivos con secretos reales. Tampoco se encontraron literales de contraseñas, tokens privados o claves privadas en el contenido inspeccionado.

Los perfiles `local` y `test` se empaquetan en `BOOT-INF/classes`, pero contienen placeholders/defaults de desarrollo y no credenciales reales. Aun así, incluir perfiles no productivos en el artefacto final es una oportunidad de higiene P1.

No se pudo ejecutar un scanner de imagen, SBOM o scanner de secretos sobre una imagen porque Docker no está disponible.

## 8. Usuario y permisos

El Dockerfile declara:

- grupo y usuario de sistema `manager`;
- `COPY --chown=manager:manager` del JAR;
- `USER manager` antes del arranque;
- `/app` como working directory.

La intención de ejecución no root es correcta. No se pudo confirmar el UID/GID efectivo, permisos de filesystem ni ausencia de capabilities porque no hubo build/inspect/run de imagen. El `RollingFileAppender` hacia una ruta absoluta externa vuelve inválida la garantía práctica de permisos y portabilidad del runtime.

## 9. `PORT` y binding

El backend configura `server.port=${PORT:${SERVER_PORT:8080}}`, por lo que el puerto runtime es configurable y tiene fallback 8080. El Dockerfile expone 8080 y el healthcheck utiliza `${PORT}`.

El Dockerfile también define `SERVER_ADDRESS=0.0.0.0`, pero `application.yaml` no muestra una propiedad `server.address` que consuma explícitamente esa variable. El comportamiento por defecto de Reactor Netty probablemente escucha en todas las interfaces, pero el contrato queda implícito y no fue confirmado con una instancia de contenedor. Debe cablearse o verificarse explícitamente antes de aprobar.

## 10. Variables de producción y fail-closed

`ProductionStartupValidation` exige en perfil productivo variables de base de datos, Redis, JWT y CORS; valida puertos, duración de JWT, orígenes CORS explícitos y una clave JWT de al menos 64 bytes UTF-8.

La configuración de DB, Redis, Flyway y server está parametrizada por entorno. La estrategia fail-closed es adecuada en el arranque productivo, pero el smoke real con variables aisladas no llegó a arrancar de forma verificable.

Existe una inconsistencia documental sobre el nombre de la variable SSL de Redis: la aplicación utiliza `REDIS_SSL`, mientras que `PRODUCTION_ENVIRONMENT_VARIABLES.md` menciona variantes como `spring.data.redis.ssl.enabled / REDIS_SSL_ENABLED`. Debe unificarse el contrato.

## 11. JVM y memoria

`JAVA_TOOL_OPTIONS` declara:

- `-XX:MaxRAMPercentage=75`;
- `-XX:InitialRAMPercentage=20`;
- `-XX:+ExitOnOutOfMemoryError`;
- encoding UTF-8;
- timezone UTC;
- entropy source no bloqueante.

La política es razonable para un contenedor con memoria limitada. No se pudo validar el valor efectivo ni la interacción con límites de memoria del runtime porque no se ejecutó el contenedor.

## 12. Filesystem y persistencia

El diseño documentado espera filesystem efímero y no dependiente de archivos locales. La inspección de código productivo no mostró escrituras de datos de negocio relevantes.

Sin embargo, el `logback.xml` empaquetado contiene explícitamente:

```text
D:/ProyectosOpenCode/MANAGER/ciberfootbolt_local/logs/app.log
D:/ProyectosOpenCode/MANAGER/ciberfootbolt_local/logs/app.%d{yyyy-MM-dd}.log
```

Esto es un **P0**: la ruta no es portable a Linux/Alpine y contradice la ejecución no root y la política stdout/stderr. Puede provocar fallo de inicialización del appender, pérdida de logs o intento de escritura en un path inexistente/no escribible.

## 13. Logging

El runtime documenta logging por stdout/stderr, pero `src/main/resources/logback.xml` configura simultáneamente consola y `RollingFileAppender` con ruta absoluta de Windows. Además, fija el root logger en `DEBUG`, por encima del nivel `INFO/WARN` esperado para producción.

La configuración empaquetada se carga automáticamente desde el JAR, por lo que no es un problema meramente documental. Debe eliminarse el archivo appender de Windows o reemplazarse por configuración portable y compatible con logs de contenedor antes de reauditar.

## 14. Healthcheck y liveness

El Dockerfile declara:

```text
wget -qO- "http://127.0.0.1:${PORT}/api/v1/health/liveness" >/dev/null || exit 1
```

El endpoint de liveness existe en la aplicación. No obstante, `wget` no se instala explícitamente en el Dockerfile y no pudo verificarse su presencia en `eclipse-temurin:21.0.8_9-jre-alpine`. Por tanto, el healthcheck no es reproduciblemente ejecutable con la evidencia disponible y queda como bloqueo de validación de imagen.

## 15. Readiness

El endpoint de readiness considera dependencias de infraestructura y responde con el estado correspondiente; existen tests de la matriz de readiness. La semántica de DB/Redis está cubierta estáticamente y por tests locales.

No se obtuvo una respuesta HTTP desde un JAR iniciado con dependencias aisladas en modo productivo. La readiness productiva queda sin smoke runtime.

## 16. Graceful shutdown

La configuración declara `server.shutdown: graceful` y timeout de lifecycle configurable, con fallback documentado de 30 segundos. El entrypoint utiliza `exec`, por lo que el proceso Java recibe correctamente la señal del contenedor en principio.

No se ejecutó un `docker stop` ni un drill real de SIGTERM. La retención/cancelación de streams y la finalización del proceso deben validarse en una prueba de contenedor posterior al cierre de los P0.

## 17. Flyway

Flyway está habilitado, usa `classpath:db/migration` y `baseline-on-migrate=false`. La configuración productiva no permite ocultar migraciones pendientes mediante baseline automático. La migración V1 y el comportamiento asociado están cubiertos por la suite local.

La ejecución productiva contra una base PostgreSQL aislada no fue completada; no se debe interpretar la suite como sustituto del smoke de arranque del artefacto.

## 18. Reproducibilidad del JAR

Se ejecutaron dos builds limpios con `mvn -q clean package -DskipTests`.

- Ambos terminaron con exit code 0.
- Tamaño en ambos casos: `42.487.084` bytes.
- SHA-256 build 1: `56E18B58EA68516397FE455B64C0256772F2940FD6885990D447CD2F556DA773`.
- SHA-256 build 2: `7EAC9DBB5541D3F75365BECDC9BC3AEC81C79BECA489BE20CF28F57CC8CA6A59`.

El resultado no es bit-a-bit reproducible. Debe identificarse y eliminarse la fuente de timestamps/metadatos variables, o documentarse un mecanismo verificable de reproducibilidad.

## 19. Frontend artifact

La revisión frontend fue positiva en lo que puede verificarse localmente:

- build development: PASS;
- build production: PASS;
- inspección del artefacto: PASS, 52 archivos;
- chequeo de encoding visible: PASS, 385 archivos;
- suite frontend: `1029 SUCCESS`, `0 failures`, `2 skipped`;
- no se encontraron referencias al test harness ni source maps en el artefacto inspeccionado;
- tamaño observado de `dist/demo/browser`: 52 archivos, 1.608.601 bytes.

La inspección detectó texto genérico asociado a código de debug dentro de un bundle minificado, pero el inspector oficial no reportó harness de pruebas, source maps ni localhost bloqueante. No se considera un rechazo independiente del frontend.

## 20. Firebase config

`firebase.json` configura `dist/demo/browser`, fallback SPA a `/index.html`, headers de caché para assets y headers de seguridad básicos. `.firebaserc.example` utiliza un placeholder, no un project ID real.

No hubo deploy ni verificación contra un proyecto Firebase. La configuración estática del hosting es coherente, pero el contrato de backend todavía no está resuelto para producción.

## 21. API routing recomendado

La recomendación para PB1.2.2 es **opción B: origen backend absoluto y configurable**, apuntando al servicio Cloud Run, con CORS explícito y configuración frontend diferenciada por entorno.

La razón es que la API usa autenticación y SSE. Un rewrite same-origin puede simplificar CORS, pero el buffering, timeouts e idle behavior del proxy Firebase no están certificados para streams. La configuración actual usa rutas relativas `/api/v1`; debe cambiarse o resolverse mediante una decisión explícita de routing antes del deploy.

## 22. SSE

El backend expone `GET /api/v1/rounds/{roundId}/stream` como `text/event-stream`. El frontend usa `fetch` con headers de autenticación, backoff `[1, 2, 5, 10, 30]s`, máximo de cinco intentos y estados de reconexión/degradación. Los tests locales cubren headers, 401, caída y reconexión.

No se validaron en cloud/proxy:

- CORS con credenciales/tokens;
- buffering de respuesta;
- idle timeout;
- límites de conexión;
- comportamiento de corte y reconexión extremo a extremo.

No hay heartbeat explícito visible en el stream. SSE queda no certificado para PB1.2.2.

## 23. Seguridad del contenedor

Aspectos positivos estáticos:

- usuario no root declarado;
- imagen runtime JRE separada del build;
- tags explícitos;
- secretos excluidos del contexto por patrones relevantes;
- no se encontraron credenciales reales en el JAR inspeccionado.

Aspectos no verificados:

- UID/GID/capabilities efectivos;
- filesystem read-only;
- scanner de vulnerabilidades;
- SBOM;
- digest final de imagen;
- tamaño final;
- comportamiento de CA/DNS y paquetes Alpine.

El logger de Windows es un bloqueo de seguridad operativa y portabilidad aunque la intención de non-root sea correcta.

## 24. Limitaciones Docker

Docker CLI no está disponible en el entorno. En consecuencia no se pudieron ejecutar:

- `docker build`;
- inspección de capas, usuario, entrypoint o healthcheck;
- `docker run`;
- smoke HTTP de liveness/readiness;
- `docker stop` y shutdown drill;
- medición de tamaño de imagen;
- SBOM y vulnerability scan.

Esta limitación se reporta como evidencia ausente, no como resultado positivo ni negativo de una ejecución que no ocurrió.

## 25. Tests backend

Última ejecución completa observada el 2026-08-01:

- comando: `mvn -q test`;
- exit code: 0;
- duración aproximada: 236,5 s;
- tests: 2564;
- failures: 0;
- errors: 0;
- skipped: 4.

También pasaron el test-compile y el conjunto focal de validación de producción, Redis, mapping, excepciones, readiness y cleanup. La suite es evidencia positiva de código, pero no reemplaza el smoke del JAR productivo ni el smoke Docker.

## 26. Tests frontend

La suite frontend terminó con `1029 SUCCESS`, `0 failures`, `2 skipped`. Los builds development y production pasaron y la inspección del artefacto reportó PASS sobre 52 archivos. El resultado frontend es aprobado dentro del alcance local de PB1.2.1.

## 27. Documentación

La documentación de diseño, runbook, contrato de variables, seguridad y smoke local es generalmente transparente sobre Docker no disponible, ausencia de deploy, ausencia de proyecto Firebase y SSE no certificado.

No obstante, `PB12_PRODUCTION_RUNTIME_FINAL_REVIEW.md` declara `PB1.2.1 IMPLEMENTATION COMPLETE - DOCKER SMOKE BLOCKED`, mientras esta auditoría identifica un P0 concreto en el JAR. Ese documento queda desactualizado y no debe usarse como aprobación hasta una nueva revisión.

La documentación de logs stdout/stderr también contradice el `logback.xml` empaquetado. El contrato `REDIS_SSL` requiere corrección documental.

## 28. P0 de PB1.2.1

1. **Logger de producción incompatible con el runtime:** el JAR carga un `RollingFileAppender` con ruta absoluta Windows `D:/.../logs`, sobre una imagen Linux Alpine y usuario no root. Contradice la política stdout/stderr y puede romper el arranque o dejar el proceso sin logging válido.
2. **Healthcheck no demostrablemente ejecutable:** usa `wget` sin instalación explícita ni verificación en la imagen. Debe corregirse o demostrarse dentro de una build/run real.
3. **Arranque productivo del JAR no certificado:** no se completó el smoke con PostgreSQL/Redis aislados; por lo tanto no existe evidencia válida de que el artefacto arranque, exponga health y cierre correctamente bajo el contrato prod.

## 29. P1 de PB1.2.1

1. Docker build, inspect, run, health, tamaño, UID, capacidades, SBOM y scanner no ejecutados por ausencia de Docker.
2. Builds limpios del JAR con hashes SHA-256 distintos; falta reproducibilidad bit-a-bit.
3. Contexto Docker más amplio de lo necesario porque no excluye de forma general documentación y archivos raíz.
4. `SERVER_ADDRESS` está declarado pero no está cableado explícitamente a `server.address`.
5. Compatibilidad Alpine, CA, DNS, `wget`, timezone y librerías nativas no demostrada.
6. No se ejecutó shutdown real con SIGTERM/`docker stop` ni smoke HTTP productivo real.
7. Routing frontend-backend y SSE en hosting/proxy no certificados; falta elegir y probar origen absoluto o rewrite compatible.
8. Inconsistencia documental del nombre de la variable SSL de Redis.
9. Perfiles `local` y `test` quedan empaquetados en el JAR, aunque no contienen secretos reales.

## 30. Gates de PB1.2.2

Quedan fuera del alcance de aprobación local de PB1.2.1 y requieren evidencia separada:

- proyecto Firebase/Cloud Run real y dominios;
- PostgreSQL y Redis gestionados;
- HTTPS, DNS, CORS y routing final;
- SSE extremo a extremo detrás del proxy elegido;
- backups, restore drill y retención;
- CI/CD, migraciones controladas y rollback;
- observabilidad, alertas y runbooks operativos;
- pruebas de carga y límites del proveedor.

Estos gates no pueden compensar ni cerrar los P0 del artefacto runtime.

## 31. Readiness para PB1.2.2

**NO preparado.**

Primero deben corregirse los P0 del artefacto, repetir build reproducible, construir/inspeccionar la imagen, ejecutar el JAR en un entorno productivo aislado, validar liveness/readiness y ejecutar shutdown. Recién después corresponde avanzar con routing Firebase/Cloud Run, SSE detrás del proxy y los gates operativos PB1.2.2.

## 32. Conclusión

El frontend hosting artifact y las suites locales presentan evidencia positiva. El backend tiene una base de contenedor razonable y validaciones de configuración bien encaminadas, pero el artefacto final no es publicable: el logger empaquetado conserva una ruta Windows absoluta y una política de logging incompatible con el runtime productivo esperado. La falta de Docker impide además verificar el healthcheck, la imagen Alpine, el usuario efectivo, el arranque, la readiness y el shutdown.

La decisión independiente es **PB1.2.1 PRODUCTION RUNTIME REJECTED**. No debe avanzarse a PB1.2.2 como estado listo hasta cerrar los P0 y obtener evidencia reproducible de build y ejecución del artefacto.
