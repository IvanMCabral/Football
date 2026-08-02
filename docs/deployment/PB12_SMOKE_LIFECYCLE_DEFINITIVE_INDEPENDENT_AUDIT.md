# PB1.2.1 Smoke Lifecycle Definitive Independent Audit

Fecha: 2026-08-01
Alcance: auditoría independiente, solo lectura, de los commits `cffcd4fc`, `2bb2436e` y `5b3580aa`.

## 1. Veredicto

`PB1.2.1 PRODUCTION RUNTIME REJECTED`

El smoke funcional del JAR pasó dos arranques, pero el gate de lifecycle no está cerrado. El runner todavía detiene PostgreSQL y Redis con `Kill($true)` dentro de `finally`, no implementa los shutdowns declarados con `pg_ctl` y `redis-cli SHUTDOWN NOSAVE`, no incorpora esos force kills al `forceKillUsed` global y no verifica de forma fail-closed la eliminación del resumen seguro. Por lo tanto, Docker no es el único gate restante.

## 2. Commits auditados

Los commits están presentes y ordenados:

1. `cffcd4fc Make production smoke dependency shutdown graceful`
2. `2bb2436e Make production smoke cleanup fail closed`
3. `5b3580aa Close PB1.2.1 smoke lifecycle audit`

La implementación efectiva revisada corresponde a `tools/run-production-jar-smoke.ps1`, `tools/GracefulProcessGroupRunner.cs`, el guard de runtime y la documentación asociada.

## 3. Git

- El árbol raíz y `front-ciber/project` estaban limpios al inicio.
- Los commits auditados solo contienen runner, helper, guard tests y documentación; no hay cambios de gameplay, simulación, datasets ni secretos nuevos en el rango revisado.
- `git diff --check` del rango histórico informa líneas en blanco al final de tres documentos nuevos de cleanup; el working tree actual no presenta ese problema.
- No se hicieron modificaciones fuera de este informe.

## 4. Process inventory

El runner crea un proceso Java por run, un helper por run, un PostgreSQL temporal y un Redis temporal. La salida de la ejecución registra los PID creados. Sin embargo, la verificación implementada por `Assert-NoResidual` solo recibe los PID Java/helper y los puertos HTTP; no recibe PID ni puertos de PostgreSQL/Redis. El inventario de infraestructura, por lo tanto, no queda verificado por el gate del runner.

## 5. Java shutdown

El helper crea Java con consola y process group propios, conserva el PID, espera el proceso y registra el exit code. La señal graceful se envía antes de detener las dependencias. Los logs inspeccionados muestran los marcadores Spring reales, y ambos runs finalizaron con exit code 130.

La implementación es válida para el caso Java/Spring probado, pero el helper registra `gracefulSignalSent` y no el nombre exacto de la señal usada. Además, si falla después de crear Java, el `finally` del runner no garantiza matar o cerrar explícitamente el Java dueño del PID.

## 6. PostgreSQL shutdown

No se encontró en el runner una llamada efectiva a `pg_ctl stop -m fast -w`. El cierre de PostgreSQL ocurre mediante `postgresProcess.Kill($true)` en `finally` cuando el proceso sigue vivo. Esto no demuestra shutdown graceful y puede ocurrir incluso después de haber asignado `status='PASS'`.

## 7. Redis shutdown

No se encontró una llamada efectiva a `redis-cli ... SHUTDOWN NOSAVE`. El cierre de Redis ocurre mediante `redisProcess.Kill($true)` y un fallback `Stop-Process -Force` en `finally`. Esto no demuestra shutdown graceful.

## 8. Global shutdown order

El orden Java es correcto en la ejecución observada: señal al backend, marcadores Spring y finalización Java. Luego el `finally` detiene Redis y PostgreSQL. El problema es que las dependencias no se detienen por su mecanismo graceful declarado y el runner no prueba que una detención forzada en `finally` invalide globalmente el PASS.

Resultado: orden parcial observado, gate global rechazado.

## 9. Force kill scan

Se encontraron caminos de fuerza en:

- `TerminateProcess` dentro del helper cuando vence el timeout de Java.
- `helperProcess.Kill($true)` si el helper no termina.
- `redisProcess.Kill($true)` y fallback `Stop-Process -Force`.
- `pgProcess.Kill($true)` y fallback `Stop-Process -Force`.

Los caminos de cleanup posterior a un fallo pueden existir, pero los de PostgreSQL/Redis no están restringidos a un estado FAIL: también quedan en el `finally` de un PASS funcional.

## 10. Force kill global

`forceKillUsed` se calcula a partir de los resultados del helper Java. No incorpora `postgresProcess.Kill`, `redisProcess.Kill`, sus fallbacks ni el kill del helper. Por eso el JSON observado con `forceKillUsed=false` no es un indicador global suficiente.

## 11. Helper failure cleanup

La ejecución controlada del helper con argumentos incompletos produjo error claro y exit code no cero. No obstante, el caso importante —fallo del helper después de crear el Java— no queda cerrado por el código: el runner no conserva una ruta garantizada para señalizar o finalizar ese Java en `finally`. Esto deja riesgo de proceso huérfano y es P0.

## 12. Marker order

En los logs inspeccionados de ambos runs aparecen, en orden:

1. `Commencing graceful shutdown. Waiting for active requests to complete`.
2. `Graceful shutdown complete`.

Se observaron dos marcadores por run, cuatro en total. Son evidencia real de Spring/WebFlux, pero el runner revisado solo busca presencia de strings y suma ocurrencias; no valida timestamps ni orden temporal mediante posiciones o timestamps. La documentación nueva declara una validación de orden que no coincide con la implementación efectiva revisada.

## 13. Negative lifecycle tests

No existe en el estado auditado una ejecución independiente reproducible de los cinco modos declarados (`postgres-stop-fails`, `redis-stop-fails`, `helper-fails-after-java`, `workspace-delete-fails`, `marker-order-invalid`) ni una interfaz `-LifecycleSelfTest`/`-TestMode` efectiva en el runner revisado. El guard estático tampoco los ejecuta.

Por ello no pueden marcarse como PASS. Las afirmaciones de los documentos `PB12_SMOKE_LIFECYCLE_CLEANUP_REPORT.md` y `PB12_SMOKE_LIFECYCLE_FINAL_REVIEW.md` no están respaldadas por el código observado y exageran el cierre.

## 14. False PASS protection

| Condición fallida | Resultado esperado | Estado auditado |
|---|---|---|
| Signal helper falla | FAIL | Java helper lo intenta, pero el camino de cleanup del Java no queda cerrado |
| Force kill usado | FAIL | No garantizado globalmente: PG/Redis quedan fuera del flag |
| Career no creada | FAIL | Cubierto por el runner |
| Segundo startup falla | FAIL | Cubierto por el runner |
| Marker Spring ausente | FAIL | Cubierto por presencia mínima |
| Java queda vivo | FAIL | Parcial; no se valida toda la infraestructura ni el fallo de helper con Java vivo |
| Puerto ocupado | FAIL | Parcial; solo se verifican puertos HTTP de app |
| Temp no limpiado | FAIL | No garantizado: el resumen seguro persiste y la eliminación ignora errores |

Además, el JSON puede reportar `residualProcesses=0` y `residualPorts=0` aun sin contar PostgreSQL/Redis.

## 15. Normal smoke

Se ejecutó desde estado limpio con build fresco. El proceso de PowerShell terminó 0 y el resultado funcional fue `PASS`, pero no es suficiente para cerrar este audit lifecycle.

## 16. KeepArtifacts smoke

Se ejecutó `-SkipBuild -KeepArtifacts` para inspeccionar evidencia. El workspace quedó intencionalmente preservado, como corresponde al flag. El helper registró PASS en ambos runs y los logs conservaron los marcadores Spring. Esto valida la inspección, no el cleanup default.

## 17. Run 1

- Modo: `PORT`.
- Puerto: `64385`.
- Java PID: `41056`.
- Liveness/readiness: `200/200`.
- Register/login/me: PASS.
- Carrera: creada.
- Flyway: `1` migración exitosa.
- Shutdown: señal enviada, markers presentes, `2751 ms`, exit code `130`.
- Java/helper y puerto HTTP residual: `0` según el resultado del runner.

En la ejecución con artifacts: puerto `52430`, Java PID `32208`, shutdown `2745 ms`.

## 18. Run 2

- Modo: `SERVER_PORT`, sin `PORT`.
- Puerto: `54515`, diferente del run 1.
- Java PID: `57508`, diferente del run 1.
- Liveness/readiness: PASS.
- Login/me post-restart: PASS.
- Flyway: continúa en `1`.
- Shutdown: markers presentes, `2736 ms`, exit code `130`.
- Java/helper y puerto HTTP residual: `0` según el resultado del runner.

En la ejecución con artifacts: puerto `63275`, Java PID `46960`, shutdown `2731 ms`.

## 19. Same database

El runner usa el mismo PostgreSQL temporal y el mismo nombre de base entre los dos arranques; la evidencia funcional muestra login/me y Flyway estable en `1`. Esto prueba persistencia mínima de la aplicación, pero no compensa el cierre no graceful de las dependencias.

## 20. Auth/career

Register, login y `/me` pasaron en el run 1; login y `/me` post-restart pasaron en el run 2. La carrera mínima fue creada y es obligatoria en el camino normal.

## 21. Flyway

Flyway reportó `1` migración exitosa en ambos arranques. No se observó reinicialización destructiva en el smoke funcional.

## 22. PORT modes

El run 1 usa `PORT`; el run 2 elimina `PORT` y usa `SERVER_PORT`. `SERVER_ADDRESS=0.0.0.0` se configura. La evidencia de los dos puertos distintos pasó.

## 23. Workspace cleanup

El runner elimina `$work` en la ruta normal, pero usa `Remove-Item -ErrorAction SilentlyContinue` sin verificar después que el directorio haya desaparecido. No existe una aserción efectiva `cleanupVerified` en el código revisado.

## 24. Safe summary cleanup

`$safeSummary` se crea en `%TEMP%` y no se elimina en el `finally`. La inspección posterior encontró nueve archivos `manager-prod-jar-smoke-*-summary.json`. Por lo tanto, `safeSummaryExists=false` y `residualTempArtifacts=0` declarados por la documentación no son reproducibles con el runner actual.

## 25. Residual processes

- Java residual: `0` observado después del smoke.
- Helper residual: `0` observado después del smoke.
- PostgreSQL residual: el entorno tenía 17 procesos PostgreSQL preexistentes; el runner no los distingue ni verifica por PID/parent process.
- Redis residual: `0` observado después del smoke.

La ausencia de Java/Redis posterior no convierte el inventario en una prueba completa, porque los procesos de dependencia no se incluyen en `Assert-NoResidual` y el runner los fuerza en `finally`.

## 26. Residual ports

- HTTP residual: `0` para los puertos de app comprobados.
- PostgreSQL residual: no verificado por el runner.
- Redis residual: no verificado por el runner.

## 27. Backend tests

Se ejecutaron compilación, focalizados y suite completa. Reports frescos: `2570 tests`, `0 failures`, `0 errors`, `4 skipped`. La documentación previa que dice `2571` está desactualizada respecto del reporte fresco de esta auditoría.

## 28. Frontend tests

Encoding, build de desarrollo, build de producción, inspección del artifact y ChromeHeadless pasaron: `1029 SUCCESS`, `0 failures`, `2 skipped`. El artifact de producción fue inspeccionado; no hay rechazo frontend dentro de este alcance.

## 29. Docker status

Docker no está disponible localmente. No se ejecutaron build, run, inspect ni `docker stop`; tampoco se demostró PID 1, healthcheck en contenedor, image size, SBOM o scan. Estos son P1/external gates, pero no el motivo principal del rechazo actual.

## 30. Documentación

El rechazo histórico de runtime debe preservarse y permanece preservado. Sin embargo, los documentos nuevos de cleanup declaran `pg_ctl`, `redis-cli`, flag global, cleanup fail-closed y cinco negative tests que no aparecen en la implementación auditada. `PB12_SMOKE_LIFECYCLE_FINAL_REVIEW.md` y `PB12_GRACEFUL_SHUTDOWN_FINAL_REVIEW.md` declaran que solo queda Docker; esa conclusión es incorrecta frente a los P0 encontrados.

## 31. P0 PB1.2.1

P0 abiertos:

1. PostgreSQL y Redis se fuerzan en `finally`, incluso en la ruta de PASS funcional; no hay shutdown graceful efectivo.
2. `forceKillUsed` no es global y puede permanecer `false` aunque se hayan forzado dependencias.
3. Un fallo del helper después de crear Java puede dejar Java huérfano.
4. Cleanup y safe summary no son fail-closed; los artifacts residuales no invalidan el PASS.
5. Los cinco negative lifecycle tests declarados no son reproducibles desde el runner auditado.
6. Los residual processes/ports no cubren PostgreSQL ni Redis.

## 32. P1 PB1.2.1

- El helper no expone en el resultado si usó `CTRL_C_EVENT` o fallback `CTRL_BREAK_EVENT`.
- El runner no valida timestamps/orden de markers con suficiente precisión.
- No hay request o SSE activo durante shutdown; queda como cobertura adicional aceptable.
- Docker/PID 1/`docker stop`, image size, SBOM/scan, cloud proxy, managed services, backup/restore y CI/CD permanecen externos.

## 33. Readiness

El runtime Java/Spring, auth/career, Flyway y los dos modos de puerto están funcionalmente listos para continuar la corrección. PB1.2.1 no está listo para declarar que el único gate restante es Docker: primero deben implementarse y ejecutarse los shutdowns graceful de PostgreSQL/Redis, el flag global, el cleanup fail-closed, el cierre del riesgo de Java huérfano, la verificación de infraestructura y los negative tests reales.

## 34. Conclusión

La evidencia independiente confirma un smoke funcional exitoso y un Java graceful observable en ambos arranques. No confirma un lifecycle completo. El veredicto definitivo es:

`PB1.2.1 PRODUCTION RUNTIME REJECTED`

No está preparado para un Docker runner como único gate restante.
