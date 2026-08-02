# PB1.2.1 Final Local Runtime Evidence Independent Audit

Fecha: 2026-08-02  
Alcance: auditoría independiente de `HEAD`, limitada a `f92f2eee`, `430f50bf` y `31aadbe5`.

## 1. Veredicto

`PB1.2.1 IMPLEMENTATION COMPLETE — DOCKER SMOKE BLOCKED`

Los P0 locales quedaron cerrados. El runner versionado calcula y limpia el summary actual, clasifica y elimina summaries históricos bajo `%TEMP%`, ejecuta los modos negativos reales, recupera Java cuando el helper muere, registra el fallback de recovery y fuerza cleanup únicamente en caminos FAIL. El smoke normal y KeepArtifacts pasan sin force kill y sin residuos de procesos o puertos. Docker es el único gate externo no ejecutado.

## 2. Commits auditados

Los commits están presentes y ordenados:

1. `f92f2eee Verify production smoke summary cleanup`
2. `430f50bf Recover Java when smoke helper exits`
3. `31aadbe5 Close PB1.2.1 local runtime evidence`

`HEAD` es `31aadbe5`.

## 3. Git

- Root y `front-ciber/project` estaban limpios antes de este informe.
- El rango auditado contiene únicamente runner, helper, lifecycle guard tests y documentación.
- No se observaron cambios de gameplay, simulación, dataset ni secretos nuevos.
- `git diff --check` del working tree es limpio.
- El diff histórico contiene una línea de whitespace en el informe independiente anterior preservado; no afecta código ni evidencia ejecutable.
- La única creación de esta auditoría es este informe.

## 4. HEAD verification

La auditoría inspeccionó directamente `git show HEAD:tools/run-production-jar-smoke.ps1` y `git show HEAD:tools/GracefulProcessGroupRunner.cs`.

La implementación versionada contiene:

- `current-run-summary.json` dentro del workspace actual;
- `currentRunSafeSummaryExists` derivado con `Test-Path`;
- `safeSummaryCleanupVerified` derivado del path real;
- limpieza y verificación de summaries históricos con patrón exacto bajo `%TEMP%`;
- `safe-summary-delete-fails` con bloqueo físico mediante file handle;
- state file inmediato de Java con `created=true`, `consoleReady=true` y `Flush(true)`;
- `--signal-existing` para recovery independiente;
- `helper-fails-after-java` y `helper-dead-recovery-signal-fails`;
- flags de fallback y force kill por familia;
- verificación final de procesos y puertos Java/helper/PostgreSQL/Redis.

## 5. Current summary calculation

El runner crea:

`%TEMP%\manager-prod-jar-smoke-<runId>\current-run-summary.json`

Escribe evidencia pre-PASS, verifica que el archivo exista, elimina el path con `Remove-PathFailClosed`, vuelve a comprobar `Test-Path` y solo permite continuar cuando `currentRunSafeSummaryExists=false` y `safeSummaryCleanupVerified=true`.

En el smoke normal real:

- `currentRunSafeSummaryExists=false`;
- `safeSummaryCleanupVerified=true`;
- `workspaceExists=false`;
- `cleanupVerified=true`;
- `residualTempArtifacts=0`.

No hay un valor literal falso utilizado como evidencia.

## 6. Current summary cleanup

El modo `safe-summary-delete-fails` crea un archivo real y mantiene un handle exclusivo para impedir su borrado. El runner devolvió:

- `status=FAIL`;
- exit code `1`;
- `currentRunSafeSummaryExists=true`;
- `safeSummaryCleanupVerified=false`;
- `residualProcesses=0`;
- `residualPorts=0`.

El bloqueo se libera en `finally`; el cleanup final eliminó el workspace y no dejó el summary permanente.

## 7. Historical summaries before/after

Antes de los modos negativos de esta auditoría:

- summaries históricos: `0`;
- workspaces smoke: `0`.

El runner aplica una política limitada al patrón exacto `manager-prod-jar-smoke-*-summary.json` y al root `%TEMP%`, verifica el conteo posterior y rechaza el flujo si no queda en cero. No realiza borrado amplio fuera de `%TEMP%`.

Después de los modos y del smoke normal:

- summaries históricos: `0`;
- summaries actuales: `0`;
- workspaces normales: `0`.

El workspace de la ejecución `-KeepArtifacts` quedó preservado intencionalmente durante la inspección: `C:\Users\ichu_\AppData\Local\Temp\manager-prod-jar-smoke-3cbdcee679e94fdebb4efc0603565939`. No contiene summary fuera de su workspace. La eliminación posterior de ese artifact de auditoría fue bloqueada por la política de seguridad del entorno, no por el runner.

## 8. Helper state file

`GracefulProcessGroupRunner.cs` crea Java con consola y process group aislados. Inmediatamente después de `CreateProcess` conoce el PID y el process group ID, escribe el state file y ejecuta `Flush(true)`.

El state file observado fue:

```json
{"javaPid":24864,"processGroupId":24864,"created":true,"consoleReady":true}
```

No contiene command line, passwords, JWT ni secretos. El runner espera el archivo dentro del workspace actual, valida `created` y `consoleReady`, y recupera PID/port antes de continuar.

Existe una ventana mínima entre `CreateProcess` y la persistencia del state file; si el helper termina dentro de esa ventana, el runner devuelve FAIL y no puede afirmar que Java fue recuperable. No existe una ventana relevante en los runs observados.

## 9. Helper original death

El modo `helper-fails-after-java` mata realmente al helper original después de que el state file fue escrito:

- `originalHelperExited=true`;
- `javaWasAliveAfterHelperExit=true`;
- `javaPidRecovered=true`;
- `recoverySignalAttempted=true`;
- `status=FAIL`;
- exit code `1`.

Esto no es una excepción mientras el helper original sigue vivo: el PID del helper original se termina y se verifica con `HasExited`.

## 10. Java alive after helper death

El runner comprueba explícitamente que Java continúe vivo después de la muerte del helper original. El resultado observado fue `javaWasAliveAfterHelperExit=true`, antes de iniciar el recovery independiente.

## 11. Java PID recovery

El PID de Java se recupera del state file actual, junto con process group ID y puerto HTTP. El runner lo conserva en `orphanRecovery` y lo reutiliza para signal/recovery y para la verificación final de residual.

## 12. Recovery signal attempt

El runner inicia una segunda instancia del helper con `--signal-existing`, el Java PID y el process group ID del state file. El helper:

- valida que el PID exista;
- intenta `AttachConsole` sobre el Java existente;
- registra `signalAttempted`, `signalUsed`, errores Win32 y resultado;
- nunca reporta graceful success si AttachConsole falla.

En el entorno auditado, AttachConsole falló en el escenario de helper muerto y se registró:

`AttachConsole to existing Java process failed`

## 13. Recovery graceful result

En `helper-fails-after-java`:

- `recoverySignalAttempted=true`;
- `recoverySignalAttemptedMode=NONE`;
- `recoverySignalUsed=NONE`;
- `javaGracefulRecoverySucceeded=false`;
- `status=FAIL`.

Esto es correcto: la recuperación graceful posterior a la muerte del helper no se falsea como PASS.

## 14. Recovery fallback

Cuando AttachConsole falla, el runner usa `Stop-Process -Id <JavaPid> -Force` únicamente en el camino FAIL, registra `javaForceKillUsedRun1=true` y actualiza `forceKillUsed=true`. Después verifica Java, helper y puerto HTTP.

El modo `helper-dead-recovery-signal-fails` confirmó explícitamente:

- helper original muerto;
- Java vivo antes del recovery;
- signal recovery fallida controladamente;
- `javaForceKillUsedRun1=true`;
- `forceKillUsed=true`;
- `status=FAIL`;
- exit code `1`;
- cero Java/helper y puertos residuales.

La limitación Windows es honesta y no abre P0 porque ocurre en FAIL, nunca permite PASS y no deja orphan.

## 15. Orphan prevention

Se revisaron los caminos posteriores a la creación de Java: fallo antes/después del state file, muerte del helper, helper sin respuesta, fallo de recovery, excepción del runner y timeout.

La secuencia efectiva es:

1. recuperar PID cuando el state file existe;
2. comprobar si Java sigue vivo;
3. intentar recovery ordenado mediante `--signal-existing`;
4. esperar el resultado;
5. usar force cleanup solo en FAIL;
6. verificar proceso y puerto;
7. emitir `status=FAIL`.

Los modos reales confirmaron cero Java huérfanos y cero puertos residuales.

## 16. Force kill flags

| Ocurrencia | Proceso | Escenario | Flag | PASS posible |
|---|---|---|---|---|
| `TerminateProcess` | Java | timeout del helper | resultado helper `forceKillUsed` | No |
| `Kill(true)` | helper | cleanup FAIL | `helperForceKillUsedRun1/2` | No |
| `Stop-Process -Force` | Java | recovery tras helper muerto | `javaForceKillUsedRun1/2` | No |
| `Kill(true)` | PostgreSQL | emergency cleanup FAIL | `postgresForceKillUsed` | No |
| `Kill(true)` | Redis | emergency cleanup FAIL | `redisForceKillUsed` | No |

El cálculo global OR combina Java, helper, PostgreSQL y Redis. Smoke normal y KeepArtifacts reportaron todos los flags `false`. Ningún `finally` permitió PASS después de force cleanup.

## 17. Negative modes

Todos los modos fueron ejecutados individualmente desde el runner real:

- `safe-summary-delete-fails`: FAIL, exit `1`, summary presente, cleanup verificado `false`, cero residuos.
- `helper-fails-after-java`: FAIL, exit `1`, helper muerto, Java detectado vivo, PID recuperado, fallback force, cero residuos.
- `helper-dead-recovery-signal-fails`: FAIL, exit `1`, recovery fallida, force flag global `true`, cero residuos.
- `postgres-stop-fails`: FAIL, exit `1`, `postgresForceKillUsed=true`, cero residuos.
- `redis-stop-fails`: FAIL, exit `1`, `redisForceKillUsed=true`, cero residuos.
- `workspace-delete-fails`: FAIL, exit `1`, `cleanupVerified=false`, cero procesos y puertos.
- `marker-order-invalid`: FAIL, exit `1`, orden inválido rechazado, cero procesos y puertos.

El guard `ProductionRuntimeArtifactGuardTest` ejecuta estos siete modos mediante el runner oficial.

## 18. Normal smoke

Se ejecutó desde estado limpio sin `-SkipBuild`:

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\run-production-jar-smoke.ps1
```

Resultado real:

```json
{"status":"PASS","port":52958,"run1PortMode":"PORT","run2Port":52994,"run2PortMode":"SERVER_PORT","javaPid":4148,"javaPidRun2":3012,"postgresPid":12908,"redisPid":22456,"startupDurationMs":6531,"startupDurationMsRun2":6055,"liveness":200,"readiness":200,"registered":true,"login":true,"me":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"secondStartup":true,"signalUsedRun1":"CTRL_C_EVENT","signalUsedRun2":"CTRL_C_EVENT","postgresGracefulStop":true,"redisGracefulStop":true,"forceKillUsed":false,"shutdownDurationMs":2699,"shutdownDurationMsRun2":2705,"javaExitCode":130,"javaExitCodeRun2":130,"shutdownMarkerOrderRun1":true,"shutdownMarkerOrderRun2":true,"residualProcesses":0,"residualPorts":0,"workspaceExists":false,"currentRunSafeSummaryExists":false,"historicalSafeSummaryCountBefore":0,"historicalSafeSummaryCountAfter":0,"safeSummaryCleanupVerified":true,"residualTempArtifacts":0,"cleanupVerified":true}
```

Todos los valores requeridos se derivan de archivos, PID, puertos o exit codes reales.

## 19. KeepArtifacts smoke

Se ejecutó con `-SkipBuild -KeepArtifacts` y devolvió PASS:

- run 1: `PORT`, puerto `53038`, Java PID `24864`;
- run 2: `SERVER_PORT`, puerto `63463`, Java PID `20424`;
- PostgreSQL PID `14144`;
- Redis PID `16832`;
- `CTRL_C_EVENT` en ambos runs;
- force kill: `false` en todas las familias;
- shutdown Java: exit code `130` en ambos runs;
- PostgreSQL/Redis: exit code `0`;
- procesos y puertos residuales: `0`;
- current summary fuera del workspace: no existe;
- workspace preservado intencionalmente;
- `postgres-data`: ausente;
- secret-like hits: `0`.

Artifacts preservados: helper executable, logs de PostgreSQL/Redis, logs de shutdown Java, `helper-state.json`, `helper-result.json`, `java.pid` y `shutdown.signal` de ambos runs. No se encontraron DB password, Redis password, JWT, Authorization, connection strings completas ni archivos de credenciales.

## 20. PostgreSQL shutdown

PASS normal y KeepArtifacts usaron `pg_ctl stop -m fast -w`, exit code `0`, PID cerrado y puerto cerrado. No se usó force kill en PASS.

## 21. Redis shutdown

PASS normal y KeepArtifacts usaron `SHUTDOWN NOSAVE`, exit code normalizado `0`, PID cerrado, puerto cerrado y sin RDB/AOF residual. No se usó force kill en PASS.

## 22. Residual processes and ports

En smoke normal, KeepArtifacts y todos los modos negativos:

- residual Java: `0`;
- residual helper: `0`;
- residual PostgreSQL creado por el runner: `0`;
- residual Redis creado por el runner: `0`;
- residual HTTP/PostgreSQL/Redis ports: `0`.

El entorno conserva 17 procesos PostgreSQL preexistentes ajenos al runner; no fueron detenidos porque la verificación usa PID exacto.

## 23. Backend tests

Se ejecutaron compilación, guard focalizado y suite completa:

- `mvn -q -DskipTests test-compile`: PASS;
- `mvn -q -Dtest=ProductionRuntimeArtifactGuardTest test`: PASS;
- `mvn -q test`: PASS;
- reports frescos: `292`;
- tests: `2571`;
- failures: `0`;
- errors: `0`;
- skipped: `4`.

## 24. Frontend tests

- encoding guard: PASS, `385` archivos;
- development build: PASS;
- production build: PASS;
- artifact inspection: PASS, `52` archivos;
- ChromeHeadless: `1029 SUCCESS`, `0` failures, `2` skipped.

## 25. Marker timestamps

El helper registra `signalTimestampUtc`. El runner valida el orden textual start/complete y la ejecución KeepArtifacts mostró los markers después del timestamp de señal en ambos runs. No existe un campo automático `shutdownMarkerAfterSignalRun1/2` basado en parsing de timestamps; esto queda como P1 de evidencia adicional, no como P0, porque el orden textual y el smoke real son correctos.

## 26. Documentation consistency

La auditoría histórica rechazada `PB12_ACTUAL_SMOKE_LIFECYCLE_FINAL_INDEPENDENT_AUDIT.md` permanece preservada. Los documentos nuevos describen de forma consistente:

- summary actual dentro del workspace;
- cleanup y summaries históricos;
- muerte real del helper;
- limitación de AttachConsole;
- fallback force solo en FAIL;
- cero Java huérfano;
- Docker no probado;
- conteos de suites coincidentes.

La documentación no afirma que la recuperación posterior a la muerte del helper sea graceful; afirma correctamente que puede fallar y usar force cleanup en un FAIL.

## 27. Docker status

`docker --version`: Docker no disponible en PATH. No se ejecutaron build, run, inspect, `docker stop`, PID 1, image size, SBOM ni scan. No se instaló Docker ni se desplegó nada.

## 28. P0 PB1.2.1

P0 locales abiertos: ninguno.

Quedaron cerrados:

- summary actual hardcodeado;
- summary no verificado;
- helper supuestamente muerto pero vivo;
- Java huérfano;
- recovery failure sin cleanup;
- force cleanup con posibilidad de PASS;
- tests negativos no ejecutables;
- residuos de procesos/puertos;
- suite roja;
- documentación exagerada.

## 29. P1 PB1.2.1

- correlación automática completa de timestamps;
- PID/duración individual de `pg_ctl` y `redis-cli`;
- SSE activo durante shutdown;
- Docker build/run, PID 1 y `docker stop`;
- image size, SBOM/scan;
- cloud deploy, managed services, backup/restore y CI/CD.

## 30. Readiness

PB1.2.1 está cerrado localmente en todos sus P0. El camino normal es graceful y fail-closed; los caminos de fallo real no producen PASS ni dejan Java, helper, PostgreSQL, Redis o puertos residuales. El proyecto queda preparado para el Docker runner externo.

## 31. Conclusión

La evidencia ejecutable de `HEAD` confirma que Docker es literalmente el único gate restante:

`PB1.2.1 IMPLEMENTATION COMPLETE — DOCKER SMOKE BLOCKED`
