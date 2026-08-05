# PB1.2.3H2 — Perfil de etapas del inicio de partido

## Alcance y correlación

El backend registra una línea estructurada por `X-Request-Id` sanitizado para
`POST /api/v1/match-engine/rounds/start`. La línea no contiene tokens,
identidades, payloads, planteles ni credenciales. Incluye todas las etapas del
contrato H2 y usa `-1` cuando la etapa no participa en el camino probado.

El cliente registra los límites T0–T7: click, POST enviado, respuesta, ruta
`/live`, conexión SSE y primer evento. La implementación mantiene un único POST
por inicio y un único stream por `roundId`.

## Traza backend reproducible

La siguiente línea proviene de `RoundControllerIdempotencyTest` en la revisión
`9a686e70` y demuestra el formato completo de la instrumentación. El test usa
dobles de puertos; por eso las etapas de persistencia externa se marcan como no
aplicables, no como cero real de producción.

| Campo | Valor |
|---|---:|
| totalMs | 16 |
| authMs | 4 |
| careerLoadMs | -1 |
| Redis GET count / ms | 0 / -1 |
| Redis SET count / ms | 0 / -1 |
| Redis serialization / deserialization ms | -1 / -1 |
| PostgreSQL query count / ms | 0 / -1 |
| lineupLoadMs / lineupValidationMs | -1 / -1 |
| fixturesLoadMs / teamsLoadMs / playersLoadMs | -1 / -1 / -1 |
| contextBuildMs / engineCreationMs | -1 / -1 |
| engineRegistrationMs / initialStatePersistenceMs | -1 / -1 |
| responseMappingMs / responseSerializationMs | 0 / -1 |
| responseBytes | -1 |
| metadata cache | miss |
| motor consultable / stream disponible | 16 ms / 16 ms |

La misma traza se emite con `success`, `roundId` y `requestId`; esos valores
operativos se conservan sólo en logs de staging y no se publican en el cliente.

## Observación pública

En diez inicios warm sobre Firebase/Render, el POST terminó antes de abrir SSE
y el primer evento llegó sin una ventana observable de `state=404` o stream=404.
Los valores client-side (ms) fueron:

| Muestra | POST hasta respuesta | Respuesta hasta stream | Respuesta hasta primer SSE |
|---:|---:|---:|---:|
| 1 | 519 | 304 | 305 |
| 2 | 367 | 319 | 320 |
| 3 | 284 | 413 | 414 |
| 4 | 302 | 515 | 515 |
| 5 | 275 | 421 | 421 |
| 6 | 1007 | 288 | 288 |
| 7 | 301 | 422 | 422 |
| 8 | 373 | 302 | 302 |
| 9 | 327 | 218 | 218 |
| 10 | 290 | 262 | 263 |

Con percentil nearest-rank, POST p50/p95/máximo = **302/1007/1007 ms**,
stream = **304/515/515 ms**, primer SSE = **305/515/515 ms**.

El inventario lógico correlacionado con la traza está en
`evidence/pb123h2/request-inventory.json`. El navegador conectado no expuso
timings de recursos individuales para los GET previos; esos campos quedan
explícitamente sin medir y no se rellenan con estimaciones.

## Conclusión de etapa

El cuello restante no está en el POST ni en SSE warm: está antes de T1, en la
navegación Angular desde la acción de la fecha hasta que `/live` puede construir
el primer view model (status de carrera, fixtures y confirmación de lineup).
La instrumentación permite medir esa espera separadamente en futuras iteraciones
sin cambiar gameplay.
