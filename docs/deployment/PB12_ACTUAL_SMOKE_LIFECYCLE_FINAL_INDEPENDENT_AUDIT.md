# PB1.2.1 Actual Smoke Lifecycle Final Independent Audit

Fecha: 2026-08-02  
Alcance: auditoría independiente de la implementación versionada en `HEAD`, limitada a los commits `3eed0bec`, `1f9319e9` y `a68ec570`.

## 1. Veredicto

`PB1.2.1 PRODUCTION RUNTIME REJECTED`

El lifecycle real mejoró sustancialmente: PostgreSQL usa `pg_ctl`, Redis usa `SHUTDOWN NOSAVE`, los cinco modos negativos son ejecutables y el smoke normal pasó. Sin embargo, el cierre local no puede declararse completo porque `safeSummaryExists` está asignado literalmente a `false` sin comprobar un path, quedan archivos históricos `manager-prod-jar-smoke-*-summary.json` en `%TEMP%`, y el modo de fallo posterior a la creación de Java no demuestra un helper realmente caído ni garantiza terminar Java si el helper ya no puede procesar la señal.

Docker no es el único gate pendiente.

## 2. Commits auditados

Los tres commits están presentes y ordenados en `HEAD`:

1. `3eed0bec Implement graceful dependency shutdown in production smoke`
2. `1f9319e9 Make smoke lifecycle evidence fail closed`
3. `a68ec570 Close actual PB1.2.1 smoke lifecycle implementation`

## 3. Git

- Root y `front-ciber/project` estaban limpios antes de crear este informe.
- El rango auditado contiene runner, helper, guard tests y documentación; no contiene cambios de gameplay, simulación o datasets, ni secretos nuevos observables.
- `git diff --check 5b3580aa..a68ec570` y el check del working tree no muestran errores de whitespace relevantes.
- La única creación permitida en esta auditoría es este informe.

## 4. Verificación del runner efectivo de HEAD

La fuente ejecutable se inspeccionó con `git show HEAD:tools/run-production-jar-smoke.ps1`; no se tomó la documentación como fuente de verdad.

La versión efectiva contiene:

- `pg_ctl stop -D $pgData -m fast -w -t $ShutdownTimeoutSeconds`;
- `redis-cli ... SHUTDOWN NOSAVE` con autenticación temporal;
- `Remove-PathFailClosed` con validación posterior y retries acotados;
- `-LifecycleTestMode` con los cinco modos requeridos;
- cálculo global de flags Java/helper/PostgreSQL/Redis;
- verificación de PID y puerto para las cuatro familias;
- cleanup del workspace temporal;
- validación de marcadores Spring por orden textual;
- state file escrito inmediatamente después de `CreateProcess`.

La discrepancia principal ya no es la ausencia del código declarado, sino que algunos campos de evidencia no se calculan realmente y algunos escenarios negativos no prueban el fallo que dicen probar.

## 5. Force-kill scan completo

| Ocurrencia | Proceso | Camino PASS/FAIL | Flag registrado | Puede producir PASS |
|---|---|---|---|---|
| `TerminateProcess` | Java | timeout del helper | `forceKillUsed` del helper | No; helper devuelve FAIL |
| `Kill($true)` / `Kill()` | helper | cleanup de fallo o timeout | `helperForceKillUsedRun1/2` | No; el camino queda FAIL |
| `Kill($true)` / `Kill()` | Java | cleanup tras fallo de Start-AppRun | `javaForceKillUsedRun1/2` | No |
| `Kill($true)` / `Kill()` | Redis | emergency cleanup tras fallo | `redisForceKillUsed` | No |
| `Kill($true)` / `Kill()` | PostgreSQL | emergency cleanup tras fallo | `postgresForceKillUsed` | No |

En el smoke normal no se observó force kill. El `finally` contiene solo cleanup de emergencia; en la ejecución PASS los cuatro procesos ya habían terminado graceful. Aun así, la seguridad depende de que los flags y la evidencia sean correctos en todos los caminos.

## 6. PostgreSQL graceful shutdown

La implementación real usa el mismo `pgData`, binario `pg_ctl` requerido desde PATH y el proceso PostgreSQL temporal iniciado por el runner. Captura `$LASTEXITCODE`, espera el proceso, comprueba PID y comprueba que el puerto quede cerrado.

En el smoke KeepArtifacts inspeccionado:

- PostgreSQL PID: `42392`.
- Puerto: temporal, incluido en la verificación de residual.
- Comando: `pg_ctl stop -D <workspace>\postgres-data -m fast -w -t 45`.
- Exit code: `0`.
- Salida: `servidor detenido`.
- Proceso residual: `0`.
- Puerto residual: `0`.

En los modos `postgres-stop-fails`, el fallo inyectado produjo `status=FAIL`, exit code `1`, `postgresForceKillUsed=true`, `forceKillUsed=true` y cero procesos/puertos residuales.

## 7. Redis graceful shutdown

La implementación real usa el Redis temporal en `127.0.0.1`, el puerto temporal, la password efímera y:

`redis-cli -h 127.0.0.1 -p <port> --no-auth-warning -a <redacted> SHUTDOWN NOSAVE`

La password no se imprime en el resultado JSON ni en la evidencia preservada. El runner captura exit code, espera el proceso, comprueba PID y puerto. Redis se inicia con `--save "" --appendonly no`, por lo que no debe dejar RDB/AOF.

En el smoke KeepArtifacts:

- Redis PID: `53312`.
- Exit code: `0`.
- Proceso residual: `0`.
- Puerto residual: `0`.
- Archivos RDB/AOF: no quedaron.

En `redis-stop-fails`, el resultado fue `status=FAIL`, exit code `1`, `redisForceKillUsed=true`, `forceKillUsed=true` y cero procesos/puertos residuales.

## 8. Java y helper

El helper versionado:

- crea `CREATE_NEW_PROCESS_GROUP | CREATE_NEW_CONSOLE`;
- conserva PID y process group ID;
- escribe `java.pid` y `helper-state.json` inmediatamente después de `CreateProcess`;
- adjunta la consola del Java;
- usa `CTRL_C_EVENT` como señal primaria;
- usa `CTRL_BREAK_EVENT` solo como fallback;
- registra señal usada, fallback, errores Win32 y timestamp;
- no usa `TerminateProcess` salvo timeout, donde el resultado es FAIL;
- espera el proceso y registra exit code.

Los dos smoke runs usaron `CTRL_C_EVENT`, fallback `false`, force kill `false` y exit code Java `130`.

### Riesgo de helper huérfano

El state file permite recuperar el PID. El modo `helper-fails-after-java` ejecutado por el runner lanza una excepción del flujo después de leer el PID; el helper todavía está vivo, recibe la señal y Java termina. Eso no equivale a simular un helper que ya se cayó. Si el helper real termina antes de procesar `shutdown.signal`, el `catch` detecta que Java sigue vivo pero solo intenta terminar el objeto helper; no ejecuta un kill directo del PID Java ni prueba una segunda ruta de cierre del Java. Ese riesgo permanece abierto como P0.

## 9. Cálculo global de `forceKillUsed`

El código sí contiene la combinación global de:

- `javaForceKillUsedRun1`;
- `javaForceKillUsedRun2`;
- `helperForceKillUsedRun1`;
- `helperForceKillUsedRun2`;
- `postgresForceKillUsed`;
- `redisForceKillUsed`.

Los resultados de los modos de fallo confirmaron el cálculo:

- PostgreSQL force cleanup: `forceKillUsed=true`.
- Redis force cleanup: `forceKillUsed=true`.
- Helper/Java cleanup: `forceKillUsed=true`.
- Smoke normal: `forceKillUsed=false`.

## 10. Lifecycle test modes

Los cinco modos existen en el runner efectivo y fueron ejecutados individualmente con `-SkipBuild -LifecycleTestMode`.

### `postgres-stop-fails`

- Exit code: `1`.
- Status: `FAIL`.
- `postgresForceKillUsed=true`.
- `forceKillUsed=true`.
- Java/helper/PostgreSQL/Redis residuales: `0`.
- Puertos residuales: `0`.

### `redis-stop-fails`

- Exit code: `1`.
- Status: `FAIL`.
- `redisForceKillUsed=true`.
- `forceKillUsed=true`.
- Java/helper/PostgreSQL/Redis residuales: `0`.
- Puertos residuales: `0`.

### `helper-fails-after-java`

- Exit code: `1`.
- Status: `FAIL`.
- PID Java recuperado desde el state file.
- `javaForceKillUsedRun1=true` y `forceKillUsed=true`.
- Java/helper/PostgreSQL/Redis residuales: `0` en el modo ejecutado.

El resultado es correcto para la inyección del runner, pero no prueba un helper que haya muerto antes de procesar la señal.

### `workspace-delete-fails`

- Exit code: `1`.
- Status: `FAIL`.
- `cleanupVerified=false`.
- `workspaceExists=true` en el JSON de fallo.
- `residualTempArtifacts=1` en el JSON de fallo.
- No quedaron procesos ni puertos residuales después del finalizer.

### `marker-order-invalid`

- Exit code: `1`.
- Status: `FAIL`.
- Ambos strings pueden existir, pero el orden inválido fue rechazado.
- No quedaron procesos ni puertos residuales.

## 11. Cleanup fail-closed

La eliminación del workspace usa `Remove-PathFailClosed`, valida que el path esté bajo `%TEMP%`, reintenta y comprueba `Test-Path` después. El modo `workspace-delete-fails` demostró que un fallo no emite PASS.

La sanitización KeepArtifacts elimina `postgres-data` y archivos de setup que podrían contener credenciales. El workspace preservado de la auditoría tuvo 21 archivos, sin `postgres-data` y sin hits de patrones de password, JWT, Authorization o connection strings completas.

La limitación crítica es que `safeSummaryExists` no se calcula: el runner lo asigna literalmente a `$false` y no busca un summary path. En `%TEMP%` permanecen nueve archivos históricos `manager-prod-jar-smoke-*-summary.json` de ejecuciones anteriores. El runner actual no los genera, pero tampoco demuestra mediante una comprobación que no exista un safe summary residual. Esto viola el requisito de evidencia fail-closed.

## 12. Smoke normal independiente

Comando ejecutado desde estado limpio:

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\run-production-jar-smoke.ps1 -SkipBuild
```

Resultado real:

```json
{"status":"PASS","port":60762,"run1PortMode":"PORT","run2Port":62134,"run2PortMode":"SERVER_PORT","javaPid":60132,"javaPidRun2":54700,"postgresPid":41320,"redisPid":35280,"startupDurationMs":6513,"startupDurationMsRun2":6462,"liveness":200,"readiness":200,"registered":true,"login":true,"me":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"secondStartup":true,"signalUsedRun1":"CTRL_C_EVENT","signalUsedRun2":"CTRL_C_EVENT","postgresGracefulStop":true,"redisGracefulStop":true,"forceKillUsed":false,"shutdownDurationMs":2761,"shutdownDurationMsRun2":2742,"javaExitCode":130,"javaExitCodeRun2":130,"shutdownMarkerOrderRun1":true,"shutdownMarkerOrderRun2":true,"residualProcesses":0,"residualPorts":0,"workspaceExists":false,"safeSummaryExists":false,"residualTempArtifacts":0,"cleanupVerified":true}
```

La ejecución funcional pasó todos sus asserts, pero el campo `safeSummaryExists=false` no es evidencia calculada por el runner.

## 13. KeepArtifacts independiente

Comando ejecutado:

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\run-production-jar-smoke.ps1 -SkipBuild -KeepArtifacts
```

Resultado real: PASS, run 1 `PORT` en `59608`, run 2 `SERVER_PORT` en `54765`, Java PID `38440`/`30608`, PostgreSQL PID `42392`, Redis PID `53312`, `CTRL_C_EVENT` en ambos runs, force kill `false`, procesos y puertos residuales `0`.

Workspace preservado para inspección:

`C:\Users\ichu_\AppData\Local\Temp\manager-prod-jar-smoke-f10ae942dbef4f74933efa2d5b1fe795`

Archivos conservados: helper executable, logs de shutdown, logs sanitizados y state/result files de ambos runs. `postgres-data` no está presente. La búsqueda de secretos encontró `0` hits en los artifacts conservados.

## 14. Orden global de shutdown

El código y la evidencia preservada muestran:

1. Java run 1 recibe `CTRL_C_EVENT`.
2. Marker Spring de inicio aparece después del timestamp de señal.
3. Marker Spring de finalización aparece después del marker de inicio.
4. Java run 1 termina con exit code `130`.
5. La misma infraestructura PostgreSQL/Redis continúa para el run 2.
6. Java run 2 recibe `CTRL_C_EVENT` y termina con exit code `130`.
7. PostgreSQL recibe `pg_ctl stop`, termina y cierra su puerto.
8. Redis recibe `SHUTDOWN NOSAVE`, termina y cierra su puerto.
9. Se validan procesos y puertos.
10. Se limpia el workspace y se emite PASS.

La secuencia funcional es correcta. La evidencia automática no relaciona el timestamp de señal con el timestamp de los markers; esa relación se confirmó manualmente en los logs KeepArtifacts.

## 15. Same database restart

El runner crea una sola infraestructura temporal antes de ambos arranques. Usa el mismo PostgreSQL PID `42392`, mismo data directory, mismo DB name y no ejecuta `initdb` entre run 1 y run 2. Flyway permanece en `1`, el usuario del run 1 sigue autenticando y `/auth/me` funciona después del restart. La carrera mínima del run 1 se crea correctamente.

## 16. Auth/career

Register, login y `/auth/me` pasaron en run 1. Login y `/auth/me` post-restart pasaron en run 2. La carrera mínima fue obligatoria y creada.

## 17. Flyway

El conteo fue exactamente `1` migration exitosa después del run 1 y continuó en `1` después del run 2. No se observó recreación destructiva del schema.

## 18. PORT modes

- Run 1: `PORT`, puerto `60762`.
- Run 2: `SERVER_PORT`, puerto `62134`, sin `PORT`.
- `SERVER_ADDRESS=0.0.0.0` configurado.
- Los puertos fueron distintos y se cerraron.

## 19. Inventario de procesos y puertos

Procesos temporales creados por el smoke normal:

- Java run 1: PID `60132`.
- Helper run 1: creado y finalizado; el PID no se conserva en el JSON final.
- Java run 2: PID `54700`.
- Helper run 2: creado y finalizado; el PID no se conserva en el JSON final.
- PostgreSQL: PID `41320`.
- Redis: PID `35280`.
- `pg_ctl` y `redis-cli`: ejecutados sincrónicamente; no se exponen como PID/duración en el JSON.

Puertos: HTTP `60762`/`62134`, PostgreSQL y Redis temporales. El runner comprueba las cuatro familias al final.

## 20. Procesos preexistentes

Antes/después del smoke no se observaron Java, Redis o helper residuales. El entorno mantiene 17 procesos PostgreSQL preexistentes ajenos al smoke; el runner usa PID exacto y no mata por nombre genérico. No se observó detención de esos procesos locales.

## 21. Resultado JSON

El JSON calcula y reporta señales por run, graceful stop de PostgreSQL/Redis, flags por familia, marker order, exit codes, residuos por familia, puertos por familia, workspace y cleanup.

Excepción: `safeSummaryExists` está hardcodeado a `false` en vez de derivarse de `Test-Path`. Por la regla del protocolo, esa evidencia no es aceptable para cerrar el gate.

## 22. Backend

Se ejecutaron `mvn -q -DskipTests test-compile`, el guard focalizado y `mvn -q test`. Reports frescos:

- `2571` tests.
- `0` failures.
- `0` errors.
- `4` skipped.
- `292` reports XML válidos.

La suite está verde y no abre un P0 por sí misma.

## 23. Frontend

Se ejecutaron:

- encoding guard: `385` archivos, PASS;
- `npm run build`: PASS;
- `npm run build -- --configuration production`: PASS;
- inspección de artifact: `52` archivos, PASS;
- ChromeHeadless: `1029 SUCCESS`, `0` failures, `2` skipped.

## 24. Documentación

La auditoría histórica rechazada `PB12_SMOKE_LIFECYCLE_DEFINITIVE_INDEPENDENT_AUDIT.md` permanece preservada. Los documentos `PB12_SMOKE_LIFECYCLE_ACTUAL_IMPLEMENTATION_REMEDIATION.md`, `PB12_SMOKE_LIFECYCLE_ACTUAL_IMPLEMENTATION_REPORT.md` y `PB12_SMOKE_LIFECYCLE_FINAL_REVIEW.md` describen correctamente la nueva superficie de código y los modos reales en gran parte.

No obstante, declaran cleanup completamente cerrado y el único gate Docker restante sin reconocer que `safeSummaryExists` es literal ni que `helper-fails-after-java` no simula un helper caído. La documentación anterior, por tanto, exagera el cierre frente a esta auditoría independiente.

## 25. Docker

Docker no está disponible localmente. No se ejecutaron build, run, inspect, `docker stop`, PID 1, image size, SBOM ni scan. Esto es un gate externo P1 y no es el motivo principal del rechazo.

## 26. P0 PB1.2.1

P0 abiertos:

1. `safeSummaryExists=false` se informa sin comprobación; existen nueve summaries históricos en `%TEMP%` y no hay verificación global de ese residuo.
2. El modo `helper-fails-after-java` prueba una excepción del runner con el helper vivo, no un helper muerto después de crear Java.
3. Si el helper real ya terminó y Java sigue vivo, el cleanup no ejecuta una terminación directa del PID Java; existe riesgo de Java huérfano.
4. Los documentos de cierre declaran que solo queda Docker, conclusión no sustentada por los dos puntos anteriores.

## 27. P1 PB1.2.1

- La validación automática de marker order no compara timestamps con `signalTimestampUtc`; la relación fue verificada manualmente en artifacts.
- PID/duración de los procesos sincrónicos `pg_ctl` y `redis-cli` no quedan en el JSON.
- No se probó SSE/request activa durante shutdown.
- Docker/PID 1/`docker stop`, image size, SBOM/scan, cloud proxy, managed services, backup/restore y CI/CD permanecen externos.

## 28. Readiness

El smoke funcional local, el shutdown Java, el shutdown graceful de PostgreSQL/Redis, los cinco modos negativos, la persistencia de DB y las suites están operativos. PB1.2.1 no está listo para declarar Docker como único gate hasta cerrar la verificación real del safe summary y el escenario de helper caído con Java recuperado y terminado.

## 29. Conclusión

La implementación versionada de `HEAD` ya contiene los mecanismos graceful esperados y la ejecución normal PASS es reproducible. La auditoría estricta, sin confiar en reportes previos, encuentra evidencia fail-closed incompleta y un riesgo de orphan Java en el camino de fallo real del helper.

Veredicto definitivo:

`PB1.2.1 PRODUCTION RUNTIME REJECTED`
