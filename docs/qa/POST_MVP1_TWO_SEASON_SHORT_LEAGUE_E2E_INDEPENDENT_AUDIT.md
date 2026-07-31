# MANAGER — Auditoría independiente del E2E de dos temporadas cortas

Fecha: 2026-07-31
Alcance: frontend Angular, backend Spring Boot/WebFlux, contratos de fixtures, ownership, evidencia E2E, persistencia, recuperación y preparación de MVP 1.
Modo: auditoría de solo lectura. No se modificaron código, tests existentes, DB, Redis ni datasets.

## 1. Veredicto

`TWO-SEASON E2E INDEPENDENTLY APPROVED WITH ISSUES`

El flujo corto de dos temporadas está suficientemente respaldado para considerarlo funcional: hay commits coherentes, evidencia de navegador/API, 12 fixtures y 12 resultados por temporada, standings independientes, cambio táctico, sustitución, recuperación y transición a temporada 2.

El proyecto todavía no queda liberado automáticamente como MVP 1 final. Permanecen gates de release y calidad que deben cerrarse: repetición independiente con salida final de las suites completas, limpieza de la evidencia/documentación, certificación específica de ascenso/descenso, correlación de consola/Network y una decisión arquitectónica sobre la organización interna.

## 2. Commits auditados

### Backend/root

- `b3363c8f` — Harden live match ownership checks.
- `880fa625` — Clarify short-league fixture contract.
- `0a4042ce` — Fix two-season E2E blockers.
- `b16f1571` — docs: certify short-league two-season E2E.
- HEAD actual: `b16f1571`.

### Frontend

- `e68cffc` — Fix career setup single division payload.
- `f737285` — Strengthen career setup team count behavior.
- HEAD actual: `f737285`.

## 3. Estado Git

- Root/backend: limpio al momento de la auditoría.
- Frontend: limpio al momento de la auditoría.
- `git diff --check` del frontend: limpio.
- `git diff --check fb933f48..b16f1571` del root: falla por espacios finales en varias líneas de `POST_MVP1_TWO_SEASON_SHORT_LEAGUE_E2E_CERTIFICATION.md`.
- No se observó un cambio productivo fuera del alcance declarado en el rango auditado.

La documentación previa que describe ambos árboles como limpios es correcta para el estado actual, pero no elimina el problema histórico de higiene del rango de commits.

## 4. Inventario de cambios

El cierre del E2E agregó o modificó:

- ownership de lectura del estado live y pruebas de 401, 403, 404 y owner nulo;
- filtro de fixtures al ámbito de la división del usuario;
- propagación del usuario autenticado a pause/resume/stop;
- resolución de `teamsPerDivision` en la capa de aplicación;
- pruebas de regresión para carrera sin tamaño explícito y control de partido;
- cambios del selector y del payload de CareerSetup;
- dos reportes QA y 36 PNG más un JSON de evidencia.

No se detectaron hardcodes de usuario E2E, país específico o cuatro equipos en los diffs auditados. Sí hay comentarios históricos y nombres de pruebas que requieren una limpieza posterior, ya registrados como deuda de mantenimiento.

## 5. Ownership y autorización

El fix de `b3363c8f` es fail-closed para `GET /api/v1/games/match/{matchId}`:

- sin autenticación: 401;
- engine inexistente: 404;
- snapshot sin `userId` o con `userId` vacío: 403 sin body;
- usuario distinto del owner: 403 sin body;
- owner correcto: respuesta 200.

La prueba agregada cubre owner nulo, falta de autenticación y match desconocido. La evidencia previa también documenta dos usuarios y rechazo del contexto de carrera ajeno. No se observó un IDOR residual en los endpoints auditados de lectura, ronda, formación, estilo o sustitución.

**Resultado:** sin P0 observado.

## 6. Contrato de fixtures

La implementación actual filtra los endpoints de división usando los dos equipos de cada fixture pertenecientes a la división del usuario. El endpoint global mantiene el alcance de liga completa.

La evidencia declara:

- `/api/v1/career/fixtures?round=1`: 2 partidos de la división de 4 equipos;
- `/api/v1/career/fixtures/all`: rondas de la división del usuario;
- `/api/v1/career/fixtures/league?round=1`: 10 partidos de las 5 divisiones españolas.

La prueba de servicio fue actualizada para impedir fixtures de otra división en las respuestas específicas. El contrato ya no presenta la ambigüedad detectada en la auditoría anterior.

**Resultado:** corregido y respaldado por código, pruebas y evidencia guardada. Falta repetir independientemente el caso completo de 20 equipos, 4 por división y 5 divisiones como prueba de release.

## 7. Bugs E2E corregidos

Se verificó en los diffs:

- `teamsPerDivision` omitido: ahora se resuelve con todos los equipos de la liga en la aplicación;
- valores explícitos inválidos: siguen rechazados;
- CareerSetup: espera un total de equipos mayor que cero antes de enviar;
- selector Angular: usa `ngValue` para conservar `null` y valores numéricos;
- estado live con owner ausente: se rechaza;
- pause/resume/stop: dejan de pasar `null` y usan el usuario autenticado;
- fixtures específicos: quedan limitados a la división del usuario;
- cache de carrera: se invalida en el flujo de inicio exitoso según las pruebas agregadas.

No se encontraron workarounds visibles exclusivos para el marathon ni flags ocultas para hacerlo pasar.

## 8. CareerSetup y team count

El componente permite valores desde 2 hasta el total de equipos cargados. Cuando se elige una división completa, el payload termina enviando el total real; cuando se elige una subdivisión, conserva el número seleccionado.

Las pruebas cubren:

- valor explícito `4`;
- espera de un total inicialmente `0` y luego `20`;
- etiquetas UTF-8 de dificultad, velocidad y división.

La cobertura de componente es de frontera HTTP y no sustituye una prueba de navegador que intercepte el request real para valores 2, 4 y máximo. La evidencia existente sí muestra selección de 4 equipos y carrera creada con 4 standings rows.

## 9. Evidencia browser/API

La carpeta `docs/qa/evidence/post_mvp1_two_season_e2e/` contiene 37 archivos: 36 PNG y `e2e-api-evidence.json`.

Hay evidencia nominal para:

- registro y login;
- selección de España y cuatro equipos;
- carrera creada;
- plantilla y once inicial;
- partido live;
- cambio táctico;
- sustitución;
- timeline final;
- reinicio de backend;
- cierre de temporada 1;
- creación de temporada 2 con standings en cero;
- partido live de temporada 2;
- standings finales de temporada 2;
- recuperación después de reinicio de Redis.

El JSON contiene datos separados para `season1`, `season2`, `transition`, `live` y `fixtureContract`.

Hallazgo de calidad: existen grupos de capturas byte a byte duplicadas, entre ellos selección de cuatro equipos, carrera creada, pantallas de ronda, estado live y standings. Esto no invalida el flujo, pero algunas capturas no son evidencia independiente de una acción distinta y deben etiquetarse o reemplazarse antes de una certificación formal.

No se encontraron secretos en el JSON auditado.

## 10. Carrera corta

La evidencia declara una liga española con 4 equipos por división y 6 rondas. El JSON registra 12 fixtures y 12 completados en temporada 1, y lo mismo en temporada 2.

Para 4 equipos en doble round-robin, el resultado esperado es:

- 6 rondas;
- 12 partidos;
- 6 partidos por club;
- 3 de local y 3 de visitante;
- 24 apariciones acumuladas en standings.

Los datos guardados son compatibles con esa matemática. La reconstrucción independiente se basa en la evidencia API versionada; no se repitió una consulta directa a PostgreSQL durante esta pasada.

## 11. Temporada 1 completa

El reporte y `e2e-api-evidence.json` registran:

- 6 rondas;
- 12 fixtures;
- 12 resultados completados;
- 0 pendientes dentro de la división;
- IDs de match sin duplicados en el conjunto certificado;
- standings con 4 clubes y played igual a 6.

**Resultado:** compatible con temporada 1 completa; falta una revalidación independiente directa sobre DB para convertirlo en evidencia primaria.

## 12. Live match de temporada 1

La evidencia documenta:

- inicio desde la UI;
- cambio de estilo;
- cambio de formación con 11 slots;
- sustitución manual en minuto 66;
- estado final `FINISHED` en minuto 90;
- eventos de sustitución en timeline;
- eventos naturales de disciplina y lesión durante la simulación.

El fix táctico actualiza el snapshot detallado después de registrar la mutación, por lo que el estado no queda limitado a la respuesta HTTP.

## 13. Impacto táctico

Está probado que estilo y formación llegan al estado live, se reflejan en slots/shape y aparecen en la información de timeline/snapshot.

No existe una comparación A/B controlada con misma seed que demuestre sensibilidad estadística en posesión, xG, tiros o canales. Eso es una limitación de evidencia, no un fallo funcional observado.

**Clasificación:** P2.

## 14. Sustitución y timeline

Temporada 1 y temporada 2 contienen evidencia de sustitución aceptada y timeline final. La certificación declara una aplicación única de la sustitución y coherencia entre resultado final y eventos.

La prueba de release pendiente es repetir un caso de doble click/retry y verificar de forma automatizada que no se agreguen dos sustituciones para la misma orden.

## 15. Standings temporada 1

La tabla independiente guardada coincide con la API:

| Equipo | P | W | D | L | GF | GA | GD | Pts |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Real Madrid | 6 | 2 | 4 | 0 | 4 | 1 | 3 | 10 |
| FC Barcelona | 6 | 2 | 4 | 0 | 2 | 0 | 2 | 10 |
| Atletico de Madrid | 6 | 1 | 3 | 2 | 2 | 3 | -1 | 6 |
| Real Sociedad | 6 | 1 | 1 | 4 | 1 | 5 | -4 | 4 |

La suma de played es 24 y los puntos son coherentes con los resultados registrados.

## 16. Reinicio y recuperación

La evidencia declara un reinicio real de backend después de la ronda 1 y recuperación en ronda 2, además de reinicio de Redis después del recorrido certificado.

Se recuperan carrera, season, ronda, fase, equipo y standings sin duplicación observada. No se inspeccionaron en esta auditoría los timestamps de proceso ni un dump directo de PostgreSQL/Redis que permita reconstruir toda la secuencia desde el almacenamiento primario.

**Resultado:** respaldado por evidencia de flujo; correlación operativa primaria pendiente.

## 17. Cierre de temporada 1

La evidencia registra ronda final completa, standings finales, cero fixtures pendientes y habilitación de transición. La temporada 1 permanece representada separadamente de la temporada 2 en el JSON.

## 18. Ascenso y descenso

El runtime informa `promotionsAvailable = true`, pero la certificación reconoce correctamente que no se verificó el movimiento exacto de clubes en todas las subdivisiones.

Clasificación correcta:

`IMPLEMENTED BUT NOT FULLY CERTIFIED BY THIS TWO-SEASON SHORT-LEAGUE PASS`

No debe venderse como ascenso/descenso completamente certificado para MVP 1 sin una prueba dedicada que compare división anterior, clubes promovidos/degradados y fixtures posteriores.

## 19. Transición a temporada 2

La transición usa la API pública de continuar carrera y registra:

- nueva season `2`;
- ronda actual `1`;
- total de 6 rondas;
- standings nuevas en cero;
- fixtures nuevos;
- temporada 1 conservada.

También se registró que un `continue` posterior al cierre legítimo de temporada 2 creó temporada 3. Eso es comportamiento esperado, no duplicación de temporada 2.

## 20. Temporada 2 completa

La evidencia registra 12 fixtures y 12 completados, 6 rondas y 4 clubes. Se documenta un live match con estilo defensivo, formación válida, sustitución y timeline final.

La ruta está funcionalmente cubierta; la misma limitación de no haber reconsultado DB directamente aplica a la certificación independiente.

## 21. Standings temporada 2

La tabla guardada coincide con el cálculo independiente:

| Equipo | P | W | D | L | GF | GA | GD | Pts |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Real Betis | 6 | 1 | 5 | 0 | 3 | 1 | 2 | 8 |
| Real Sociedad | 6 | 2 | 2 | 2 | 2 | 3 | -1 | 8 |
| Athletic Club | 6 | 1 | 4 | 1 | 1 | 1 | 0 | 7 |
| Villarreal CF | 6 | 0 | 5 | 1 | 1 | 2 | -1 | 5 |

La suma de played es 24 y no se observa carry-over de puntos desde temporada 1.

## 22. Integridad DB/API

La evidencia de cierre se obtuvo mediante UI y APIs públicas sin mutar DB directamente para completar partidos. El JSON contiene fixtures, resultados, standings, transición, live final y eventos de sustitución.

No contiene un snapshot primario completo de tablas de PostgreSQL para carreras, temporadas, resultados, eventos, lesiones, sanciones y mutaciones. Por lo tanto:

- integridad API: respaldada;
- integridad DB completa: no revalidada independientemente en esta pasada;
- contaminación evidente entre temporadas: no observada.

**Clasificación:** P2 de evidencia, no P0/P1 observado.

## 23. Idempotencia

La evidencia registra:

- retry de `continue` en `PRE_MATCH` sin crear otra temporada;
- retry de `next-round` sin duplicar fixtures;
- IDs de partidos únicos;
- creación legítima de temporada 3 solamente después de cerrar temporada 2.

Queda pendiente ampliar el caso a doble click, refresh durante transición, retry de inicio de temporada y doble finalización con una prueba automatizada de release.

## 24. Lesiones, disciplina y fatiga

En el recorrido se observaron eventos de lesión y disciplina. La fatiga, disponibilidad posterior y recuperación están cubiertas principalmente por pruebas de backend y no por una matriz browser completa de dos temporadas.

**Clasificación:** P2 de evidencia manual.

## 25. Browser console y Network

No se entregó en la carpeta auditada un export completo de consola y Network por cada fase. No es posible certificar independientemente, solo con screenshots, ausencia total de 5xx inesperados, requests duplicados, loaders eternos o stale state.

**Clasificación:** P2; debe agregarse al paquete de release.

## 26. Backend tests

Validación realizada en esta auditoría:

- `mvn -q -DskipTests test-compile`: OK.
- Se intentó `mvn -q test`, pero la ejecución concurrente con la suite frontend no alcanzó un cierre dentro del límite de 180 segundos.
- Los reportes Surefire disponibles contienen 2521 tests, 0 failures, 0 errors y 4 skipped, pero no deben confundirse con una corrida completa final de 2529: son reportes parciales/previos a la certificación declarada.

La certificación existente declara 2529 tests, 0 failures, 0 errors y 4 skipped. Se conserva como evidencia declarada, pero hay que repetir la suite completa en forma aislada y guardar su salida final para cerrar este gate.

## 27. Frontend tests/build

Validación realizada en esta auditoría:

- `npm run pretest`: OK; guard de encoding sobre 381 archivos.
- `npm run build`: OK; build Angular generado correctamente.
- `npm test -- --watch=false --browsers=ChromeHeadless`: no alcanzó un cierre final dentro de la ejecución concurrente limitada.

La certificación existente declara 1026 SUCCESS, 0 failures y 2 skipped. Debe repetirse en forma aislada para que el resultado sea reproducible como gate de release.

## 28. Encoding

El guard actual pasa y el código de CareerSetup contiene textos UTF-8 válidos como `Fácil`, `Difícil`, `Rápida` y `4ª`. Las ocurrencias encontradas en el test de PlayerCard son textos internacionales válidos y patrones de prueba, no mojibake.

La auditoría anterior detectó correctamente el riesgo; el fix de `f737285` agregó cobertura específica.

## 29. Documentación existente

La certificación de dos temporadas es consistente con los datos guardados, pero su veredicto `TWO-SEASON E2E APPROVED` es más amplio que la evidencia primaria disponible en tres puntos:

- promoción/descenso no está completamente certificado;
- DB primaria no está anexada de forma completa;
- console/Network no tiene un paquete correlacionado;
- el rango histórico falla `git diff --check` por espacios finales.

Los reportes anteriores deben conservar sus conclusiones históricas, pero este documento es el nivel de severidad más prudente para decidir la salida.

## 30. Hallazgos P0

Ninguno observado.

No se observó corrupción, acceso cruzado confirmado, duplicación de temporada certificada ni pérdida de carrera.

## 31. Hallazgos P1

Ningún P1 funcional observado en el flujo corto certificado.

El cierre de la suite completa sigue siendo un gate de release pendiente por falta de una corrida independiente con salida final, pero la ejecución disponible no mostró failures ni errors.

## 32. Hallazgos P2

1. El rango de commits root falla `git diff --check` por espacios finales en documentación.
2. Hay capturas duplicadas byte a byte en la evidencia de acciones distintas.
3. No hay A/B estadístico controlado para demostrar impacto cuantitativo de táctica.
4. Ascenso/descenso está implementado pero no completamente certificado.
5. La integridad de DB primaria no está anexada de forma independiente; la evidencia disponible es API/JSON.
6. No hay un paquete completo de consola y Network.
7. La suite completa de backend y frontend debe repetirse de forma aislada y cerrar con salida final.
8. El backend mantiene dos convenciones de puertos: `domain/port` y `domain/ports`.
9. El frontend mezcla servicios globales en `core` con servicios por feature sin una regla escrita de límites.
10. `application/service/simulation/detailed` concentra una cantidad muy alta de clases y necesita una auditoría de cohesión posterior al MVP.
11. La raíz contiene logs locales de gran tamaño, aunque estén ignorados.
12. No se encontró contrato OpenAPI/Swagger versionado; los contratos frontend-backend dependen de controladores, DTOs y servicios distribuidos.

## 33. Preparación para MVP 1

### Funcionalmente

El E2E de dos temporadas cortas está respaldado y no presenta P0/P1 funcional abierto en la evidencia revisada.

### Para release

No lo marcaría todavía como completamente listo para push/merge de MVP 1 hasta cerrar:

1. corrida completa aislada de backend y frontend con resultados finales;
2. limpieza de espacios finales y duplicados de evidencia;
3. certificación específica de ascenso/descenso o declaración explícita de fuera de alcance;
4. evidencia directa de DB y consola/Network;
5. revisión de dependencias y `npm audit`;
6. decisión documentada sobre `domain/port` versus `domain/ports` y límites del frontend.

## 34. Conclusión

Los fixes de ownership, fixtures, CareerSetup, transición, live táctico, sustitución y recuperación están razonablemente implementados y respaldados por pruebas, código y evidencia E2E. La funcionalidad corta de dos temporadas puede considerarse aprobada con issues.

La recomendación independiente es mantener el MVP 1 en estado **release candidate con issues**, no declararlo todavía como salida definitiva. Los pendientes restantes son principalmente de evidencia, reproducibilidad, higiene y arquitectura; deben cerrarse antes de afirmar que todo el MVP está certificado para producción.

## Reproducibility closure status

Date: 2026-07-31

The historical verdict is preserved:

`TWO-SEASON E2E INDEPENDENTLY APPROVED WITH ISSUES`

New closure evidence was added without modifying product code, tests, datasets or database state:

- Backend isolated suite: `2529` tests, `0` failures, `0` errors, `4` skipped.
- Frontend isolated suite: development build PASS, production build PASS, `1026 SUCCESS`, `0` failures, `2` skipped.
- Evidence index: 36 PNG screenshots indexed with SHA-256 hashes and duplicate groups documented instead of deleted blindly.
- Standings reconciliation: season 1 and season 2 standings differences are empty against the certified API artifact.
- Browser console: authenticated traversal completed with zero console errors.
- Browser/network: no unexpected 5xx responses observed; detail API probes for historical match ids returned 404/400 and remain explicit issues.
- Restart/recovery: backend PID changed and authenticated recovery probes succeeded for login, lineup, team and squad; `/api/v1/career/debug` returned 404.
- PostgreSQL direct evidence: connection validated, but the certified short-league career is absent from PostgreSQL `games/matches/standings`; the runtime career evidence is therefore not PostgreSQL-backed in the current implementation.
- NPM audit: sanitized summary recorded; high/critical dependency advisories remain outside this evidence-only closure.

This closure improves reproducibility and evidence hygiene, but it does not convert the historical verdict to full release approval because release evidence still contains material issues outside the authorized evidence-only scope.
