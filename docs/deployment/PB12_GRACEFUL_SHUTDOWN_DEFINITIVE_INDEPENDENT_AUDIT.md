# PB1.2.1 — Auditoría independiente definitiva de graceful shutdown

Fecha: 2026-08-01

Alcance: únicamente los commits `51d3dc18`, `44027ac2` y `5b194e26`, el helper de terminación, el runner de smoke, sus evidencias locales y las suites declaradas. No se reauditaron gameplay, simulación, datasets, PB1.1, cloud ni Firebase.

## 1. Veredicto

**PB1.2.1 PRODUCTION RUNTIME REJECTED**

El mecanismo backend de graceful shutdown sí entrega una señal de consola real, obtiene exit code 130 y produce markers reales de Spring/Netty en ambos arranques. El smoke funcional independiente pasó con dos JAR distintos, la misma DB temporal, `PORT`, `SERVER_PORT`, auth, carrera, Flyway y cero procesos Java/puertos HTTP residuales.

Sin embargo, el único gate local no está cerrado de forma válida por dos defectos del lifecycle runner:

1. El bloque `finally` fuerza el cierre de PostgreSQL y Redis también después de un resultado PASS mediante `Kill($true)`. El resultado `forceKillUsed=false` solo cubre el helper/Java, no las dependencias.
2. La limpieza del `safeSummary` temporal no existe y la limpieza del workspace usa `-ErrorAction SilentlyContinue`, por lo que una falla de cleanup no invalida el PASS.

El Docker smoke sigue siendo externo, pero no es la única condición pendiente. El resultado correcto del backend Java no habilita un PASS global del runner cuando el cleanup PASS usa force kill y puede ocultar fallos de limpieza.

## 2. Commits auditados

- `51d3dc18 Prove graceful production jar shutdown`
- `44027ac2 Harden production smoke lifecycle`
- `5b194e26 Close PB1.2.1 graceful shutdown audit`

Los tres commits están presentes y ordenados en el historial actual, con `5b194e26` como HEAD, seguido por `44027ac2`, `51d3dc18` y `3b41073a`.

## 3. Git y alcance de cambios

Los árboles root y frontend estaban limpios antes del informe. El diff `3b41073a..5b194e26` está limitado al helper, runner, guard tests y documentación de runtime/shutdown. No se observaron cambios de gameplay, simulación, datasets ni secretos nuevos.

El working tree no reportó errores con `git diff --check`. La comparación histórica `git diff --check 3b41073a..5b194e26` reportó únicamente nuevas líneas en blanco al final de tres documentos agregados:

- `PB12_GRACEFUL_SHUTDOWN_REMEDIATION.md`;
- `PB12_GRACEFUL_SHUTDOWN_SMOKE_REPORT.md`;
- `PB12_GRACEFUL_SHUTDOWN_FINAL_REVIEW.md`.

No se modificaron esos documentos.

## 4. Helper `GracefulProcessGroupRunner`

El helper versionado usa P/Invoke de Kernel32 y:

- `CREATE_NEW_PROCESS_GROUP`;
- `CREATE_NEW_CONSOLE`;
- `STARTF_USESTDHANDLES`;
- `CreateProcess` para crear el proceso desde el command line recibido;
- stdout/stderr redirigidos a handles heredables;
- PID y handles propios obtenidos desde `PROCESS_INFORMATION`;
- cierre del thread handle inmediatamente después de crear el proceso;
- cierre del process handle en `finally`;
- `WaitForSingleObject` para esperar al proceso;
- `GetExitCodeProcess` para registrar exit code;
- `SetConsoleCtrlHandler(NULL, TRUE)` para que el helper ignore el Ctrl+C que envía;
- `FreeConsole` y `AttachConsole(processInfo.dwProcessId)`;
- `GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0)` como señal primaria;
- `CTRL_BREAK_EVENT` únicamente si la llamada primaria devuelve false;
- `TerminateProcess` solo después de agotar el timeout de graceful;
- resultado FAIL y exit code 2 si se utilizó force kill.

El helper no acepta un PID arbitrario para señalizar: crea el proceso y conserva el PID retornado por Windows. El proceso Java queda en una consola propia, por lo que el `dwProcessGroupId=0` se limita en la práctica a esa consola dedicada. No se dirige a la consola interactiva del auditor.

La señalización no registra cuál de los dos eventos fue usado; solo registra `gracefulSignalSent=true`. El fallback existe en código, pero no fue forzado durante el smoke.

## 5. Validez de `CTRL_C` / `CTRL_BREAK`

El mecanismo es técnicamente válido para un proceso Java foreground en Windows en el caso observado:

- Java fue creado con consola y process group propios;
- el helper se adjuntó a la consola del proceso creado;
- se generó un evento de consola, no una terminación directa;
- el proceso Java terminó con exit code 130;
- se observaron markers de graceful shutdown de Spring/Netty después de la señal;
- no se usó `Stop-Process` sobre Java en el runner principal.

La limitación técnica es que `GenerateConsoleCtrlEvent(..., 0)` no utiliza explícitamente el PID como process group target; la seguridad depende de `CREATE_NEW_CONSOLE`. La ausencia de una etiqueta que indique `CTRL_C` o `CTRL_BREAK` usado deja evidencia de señal incompleta, pero no invalida el caso observado porque los markers y el exit code respaldan la terminación controlada.

## 6. Compilación y ejecución del helper

El runner compila `tools/GracefulProcessGroupRunner.cs` con `Add-Type` como `ConsoleApplication` y escribe `GracefulProcessGroupRunner.exe` dentro del workspace temporal del smoke. No deja binarios en el repositorio.

La compilación se ejecutó correctamente durante el smoke completo. También se invocó el binario compilado con argumentos incompletos; devolvió exit code `1` y el error claro `ArgumentException: Missing --command`.

La compilación y el caso controlado de error son deterministas. El caso de proceso inexistente por `CreateProcess` está implementado con `Win32Exception`, aunque no fue usado como una segunda ejecución de infraestructura.

## 7. Runner de production smoke

El runner:

- no carga `.env`;
- usa PostgreSQL y Redis temporales con puertos aleatorios;
- genera usuario, DB, passwords y JWT efímeros;
- usa `SPRING_PROFILES_ACTIVE=prod`;
- ejecuta dos procesos Java distintos;
- utiliza `PORT` en run 1;
- elimina `PORT` y utiliza `SERVER_PORT` en run 2;
- define `SERVER_ADDRESS=0.0.0.0`;
- exige liveness/readiness;
- exige register/login/me;
- exige IDs de liga/equipo y carrera mínima;
- exige exactamente una migración Flyway exitosa en cada arranque;
- exige graceful shutdown en ambos runs;
- exige `forceKillUsed=false` en ambos resultados del helper;
- comprueba cero procesos/puertos residuales de Java/helper y de la aplicación;
- detiene PostgreSQL y Redis después de que Java haya terminado.

El flujo funcional está bien protegido para la carrera y el segundo arranque. El problema está en el cierre y en la forma de clasificar cleanup, descrito en las secciones siguientes.

## 8. Prevención de falso PASS

La protección funcional de PASS es buena para la mayoría de las condiciones:

- helper ausente o error previo al PID: lanza excepción;
- health incompleto: lanza excepción;
- register/login/me incompleto: lanza excepción;
- carrera vacía o respuesta vacía: lanza excepción;
- Flyway distinto de exactamente 1: lanza excepción;
- segundo startup/login/me fallido: lanza excepción;
- resultado helper distinto de PASS: lanza excepción;
- marker Spring ausente: lanza excepción;
- Java/helper o puertos de aplicación residuales: lanza excepción.

Pero no protege completamente contra falso PASS:

- `forceKillUsed` no incluye force kill de PostgreSQL/Redis en `finally`;
- `Kill($true)` de las dependencias ocurre aun cuando `$status` ya es `PASS`;
- `Remove-Item ... -ErrorAction SilentlyContinue` puede fallar sin cambiar el estado a FAIL;
- el `safeSummary` escrito fuera de `$work` nunca se elimina;
- `residualProcesses` y `residualPorts` solo se calculan para Java/helper y puertos HTTP, no para PostgreSQL/Redis.

Tabla de condiciones:

| Condición fallida | Resultado esperado | Resultado del código |
|---|---|---|
| Signal helper falla | FAIL | FAIL por excepción; puede dejar Java huérfano si ya fue creado |
| Force kill usado | FAIL | FAIL para Java; no cubre force kill de PostgreSQL/Redis en `finally` |
| Career no creada | FAIL | FAIL |
| Segundo startup falla | FAIL | FAIL |
| Marker Spring ausente | FAIL | FAIL |
| Java queda vivo | FAIL | FAIL si se detecta dentro del helper/runner |
| Puerto ocupado | FAIL | FAIL para puertos de app; no audita todos los puertos de infraestructura |
| Temp no limpiado | FAIL | No garantizado; cleanup silencioso y summary persistente |

## 9. Evidencia de Spring graceful shutdown

En una ejecución con artifacts conservados se inspeccionaron los logs asociados a ambos PIDs. Los markers concretos fueron, por run:

1. `Commencing graceful shutdown. Waiting for active requests to complete`;
2. `Graceful shutdown complete`.

Run 1 registró ambos markers a las 23:38:11.884 y 23:38:11.886. Run 2 registró ambos a las 23:38:24.938 y 23:38:24.940. El orden es correcto y las salidas están separadas por el archivo correspondiente a cada proceso Java.

`shutdownMarkersObserved=4` significa **dos markers por run**, no cuatro eventos distintos. No apareció `Shutdown completed`. La evidencia es suficiente para demostrar que el SpringApplication shutdown hook inició el graceful shutdown del servidor Netty y que Netty terminó su cierre; no demuestra por sí sola el cierre individual de todos los connection pools ni de cada bean del contexto.

El runner solo cuenta presencia por string y exige al menos un marker, sin verificar orden, timestamps o contenido de cierre de pools. La inspección independiente sí confirmó orden y timestamps en ambos logs.

## 10. Orden de shutdown

El orden funcional observado es:

1. se crea `shutdown.signal`;
2. el helper envía el evento de consola;
3. Spring registra `Commencing graceful shutdown`;
4. Netty registra `Graceful shutdown complete`;
5. Java termina con exit code 130;
6. se confirma ausencia del backend y de sus puertos HTTP;
7. el flujo pasa al cleanup de dependencias.

No se observó que Redis o PostgreSQL fueran detenidos antes de Java. El runner comprueba que ambos sigan vivos antes del cierre del bloque final. No obstante, el cierre posterior se hace con `Kill($true)`, incluso para PASS, lo que invalida la conformidad del lifecycle completo.

## 11. Force kill

El helper contiene `TerminateProcess` únicamente como fallback después del timeout de graceful y marca `forceKillUsed=true`; en ese caso el resultado es FAIL.

El runner también contiene force kill en varios caminos de cleanup:

- helper: `$run.helperProcess.Kill($true)` en timeout/error;
- Redis: `$redisProcess.Kill($true)` en `finally`;
- PostgreSQL: `$pgProcess.Kill($true)` en `finally`.

Los dos últimos se ejecutan en el camino normal, después de haber asignado `$status = 'PASS'`, y no alimentan `forceKillUsed`. Por lo tanto, la afirmación documental “no force kill used in PASS” es cierta únicamente para Java, no para el lifecycle total. Esto es un P0 del contrato solicitado.

## 12. Exit code 130

Los dos smoke runs independientes terminaron con exit code Java `130`. Es coherente con la interrupción por evento de consola y no se interpreta como crash porque coincide con:

- `gracefulSignalSent=true`;
- `gracefulShutdownObserved=true`;
- markers Spring/Netty;
- duración acotada;
- `forceKillUsed=false` en el helper.

El runner no exige exit code 0, lo cual es correcto para este mecanismo Windows. La documentación explica esta semántica.

## 13. Smoke independiente ejecutado

Se ejecutó desde el repositorio limpio:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\run-production-jar-smoke.ps1
```

Resultado real, sin reutilizar el JSON de documentación:

```json
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":64385,"run1PortMode":"PORT","run2Port":54515,"run2PortMode":"SERVER_PORT","javaPid":41056,"javaPidRun2":57508,"postgresPid":59800,"redisPid":55236,"startupDurationMs":6650,"startupDurationMsRun2":6478,"liveness":200,"readiness":200,"registered":true,"login":true,"me":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"secondStartup":true,"gracefulSignalSent":true,"gracefulShutdownObserved":true,"forceKillUsed":false,"shutdownDurationMs":2751,"shutdownDurationMsRun2":2736,"javaExitCode":130,"javaExitCodeRun2":130,"shutdownMarkersObserved":4,"residualProcesses":0,"residualPorts":0,"localLogArtifacts":0}
```

El proceso terminó con exit code del runner `0`. No se imprimieron passwords ni tokens.

## 14. Run 1

| Check | Resultado independiente |
|---|---|
| Modo | `PORT` |
| Puerto | `64385` |
| Java PID | `41056` |
| Startup | `6650 ms` |
| Liveness | `200` |
| Readiness | `200` |
| Register/login/me | PASS |
| Carrera mínima | PASS |
| Flyway | `1` |
| Señal graceful | PASS |
| Markers | presentes; 2 en inspección equivalente |
| Force kill Java | `false` |
| Shutdown | `2751 ms` |
| Exit code Java | `130` |

## 15. Run 2

| Check | Resultado independiente |
|---|---|
| Modo | `SERVER_PORT`, sin `PORT` |
| Puerto | `54515` |
| Java PID | `57508`, distinto de run 1 |
| Startup | `6478 ms` |
| Liveness | `200` |
| Readiness | `200` |
| Login/me post-restart | PASS |
| Misma DB temporal | PASS por el mismo `$dbName` y `pgProcess` |
| Flyway | `1` |
| Señal graceful | PASS |
| Markers | presentes; 2 en inspección equivalente |
| Force kill Java | `false` |
| Shutdown | `2736 ms` |
| Exit code Java | `130` |

## 16. Persistencia entre arranques

Run 2 se inicia contra el mismo PostgreSQL temporal y el mismo `$dbName` que run 1. No se reinicializa el schema ni se crea una segunda DB. La migración exitosa permanece exactamente en `1`, y el usuario creado en run 1 puede hacer login y recuperar `/auth/me` en run 2.

No se exige restore Redis en este alcance. El smoke tampoco crea una segunda carrera, por lo que no evalúa duplicación de datos de carrera; el contrato mínimo declarado es login/me post-restart y Flyway estable, ambos cumplidos.

## 17. PORT

La evidencia independiente cubre:

- run 1 con `PORT=64385`;
- run 2 con `SERVER_PORT=54515` y `PORT` eliminado;
- `SERVER_ADDRESS=0.0.0.0` en ambos;
- aplicación configurada como `server.port=${PORT:${SERVER_PORT:8080}}`.

La guard test valida el fallback estático. No se ejecutó un smoke completo con el default 8080 ni con puerto inválido; no son el motivo del rechazo actual.

## 18. Cleanup temporal

El smoke normal elimina `$work` al final del PASS. En la ejecución sin flags, el directorio de trabajo fue limpiado. La opción `-KeepArtifacts` conserva explícitamente artifacts y se usó en una segunda ejecución solo para inspeccionar markers.

El cleanup no es completamente conforme:

- `$safeSummary` se crea directamente bajo `%TEMP%` y nunca se elimina;
- el cleanup de `$work` usa `Remove-Item -ErrorAction SilentlyContinue`, sin comprobar que haya desaparecido;
- no se valida que stdout/stderr, password artifacts o el summary hayan sido eliminados;
- el `finally` mata PostgreSQL/Redis aunque el resultado previo sea PASS.

La búsqueda posterior registró `java=0` y `redis-server=0`; había procesos PostgreSQL preexistentes del entorno. También quedaron summaries de smoke en `%TEMP%` y un workspace conservado por la ejecución explícita con `-KeepArtifacts`. Por el contrato pedido, cleanup no puede marcarse como completamente PASS.

## 19. Procesos y puertos

El resultado del smoke independiente informó `residualProcesses=0` y `residualPorts=0` para los dos Java, los dos helpers y los puertos HTTP `64385`/`54515`. Después de la ejecución no quedó Java ni Redis del smoke.

El runner no incluye PostgreSQL/Redis en la función `Assert-NoResidual`; solo comprueba que sigan vivos antes del cleanup y luego los fuerza a terminar. Por eso sus campos `residualProcesses=0` y `residualPorts=0` no representan todos los PIDs/puertos creados.

No se observaron indicios de que el runner matara los procesos PostgreSQL preexistentes; los procesos reportados fueron los PIDs temporales creados por el propio runner.

## 20. SSE o request activa

El smoke no mantiene una request ni stream SSE activo durante el shutdown. Esto no se considera P0 en esta auditoría. La evidencia demuestra graceful shutdown HTTP básico de Spring/Netty; el comportamiento con SSE activo queda como P1 para Docker/cloud, sin exigir completar un partido.

## 21. Tests backend

Se ejecutó:

- `mvn -q -DskipTests test-compile`: PASS;
- tests focalizados de runtime/guard/health/Redis/mapping/excepciones: PASS;
- `mvn -q test`: PASS.

Los reportes frescos de la suite completa fueron 292 y totalizaron:

- 2570 tests;
- 0 failures;
- 0 errors;
- 4 skipped.

La suite no contiene un test unitario directo del helper Win32; la compilación y el smoke real son la evidencia del helper.

## 22. Tests frontend

Se ejecutaron encoding, build development, build production, inspección y ChromeHeadless:

- encoding: PASS, 385 archivos;
- build development: PASS;
- build production: PASS;
- artifact inspection: PASS, 52 archivos;
- production sin test harness, localhost bloqueante ni source maps: PASS;
- frontend: `1029 SUCCESS`, `0 failures`, `2 skipped`.

El lazy chunk del test harness aparece en el build development, pero no en el artifact production inspeccionado.

## 23. Dockerfile y limitación Docker

La reconfirmación estática mantiene:

- entrypoint con `exec java`;
- Java como proceso esperado dentro del contenedor;
- base Jammy explícita;
- curl instalado para liveness;
- usuario non-root;
- `PORT`/`SERVER_ADDRESS` configurables;
- logging solo por consola;
- ausencia de secretos en Dockerfile.

Docker no está disponible localmente. No se ejecutaron build, run, inspect, tamaño de imagen, SBOM, scan, PID 1 ni `docker stop`. Estas son limitaciones P1/external, no se inventaron resultados.

## 24. Documentación

La documentación nueva preserva la auditoría REJECTED anterior y describe correctamente el mecanismo de consola, exit code 130, dos startups y limitación Docker. Los hashes, conteos de suites y resultados declarados coinciden con el smoke independiente en los campos funcionales.

La afirmación documental `forceKillUsed=false` debe interpretarse como referida al helper/Java: el runner sí utiliza `Kill($true)` para Redis/PostgreSQL en su `finally` normal. Asimismo, la afirmación de cleanup PASS no incluye el `safeSummary` persistente. Por eso `PB12_GRACEFUL_SHUTDOWN_FINAL_REVIEW.md` sobrecalifica el estado como “únicamente Docker bloqueado”.

## 25. P0 PB1.2.1

1. **Force kill en camino PASS:** `finally` ejecuta `$redisProcess.Kill($true)` y `$pgProcess.Kill($true)` después de los dos graceful shutdowns y después de asignar `$status='PASS'`. El booleano reportado `forceKillUsed=false` no cubre estas dependencias.
2. **Cleanup no fail-closed:** `$safeSummary` queda en `%TEMP%` siempre y la eliminación de `$work` silencia errores. Un cleanup incompleto no cambia PASS a FAIL.
3. **Cleanup/error path de helper puede dejar Java huérfano:** si el helper falla después de crear Java pero antes de devolver resultado, el `finally` del runner no mata explícitamente el PID Java, solo el helper y las dependencias.

## 26. P1 PB1.2.1

- no se registra cuál evento (`CTRL_C` o `CTRL_BREAK`) fue usado;
- el fallback a `CTRL_BREAK` solo ocurre ante fallo de la API, no ante ausencia de respuesta dentro del timeout;
- markers se cuentan por presencia y no por orden/timestamp dentro del runner;
- `shutdownMarkersObserved=4` son dos markers por run, no cuatro eventos distintos;
- no se verifican explícitamente connection pools/context close markers;
- `residualProcesses`/`residualPorts` no incluyen PostgreSQL/Redis;
- no se prueba SSE activo durante shutdown;
- Docker/PID 1/`docker stop`, image size, SBOM y scan siguen pendientes;
- quedan gates cloud y operativos fuera de alcance.

## 27. Readiness y conclusión

**Readiness para el Docker runner: parcial.** La imagen y el JAR están listos para una ejecución Docker externa, y el smoke Windows/JAR ya demostró la secuencia graceful funcional. Pero PB1.2.1 no está aprobado porque el runner de evidencia fuerza PostgreSQL/Redis en PASS, puede ocultar errores de cleanup y deja summaries temporales.

La conclusión independiente es **PB1.2.1 PRODUCTION RUNTIME REJECTED**. Para que el único gate restante sea realmente Docker, el lifecycle runner debe hacer cleanup no forzado después de PASS, contabilizar force kill de todas las dependencias, eliminar/verificar todos los temporales y dejar FAIL ante cualquier error de cleanup o helper.
