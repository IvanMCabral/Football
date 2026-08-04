# PB1.2.3C — Cold start y restart: drill definitivo

Fecha: 2026-08-04
Backend: <https://manager-staging-api.onrender.com>
Servicio: Render `manager-staging-api`

## Resultado

**NOT CERTIFIED — REJECTED FOR PB1.2.3C CLOSURE**

Se observaron señales reales de cold start en logs y se preparó un partido público en estado `RUNNING`, pero no se completó un reinicio controlado del servicio durante ese partido. No se inventa evidencia de recuperación.

## Evidencia de arranque

Render registró:

- Perfil `prod`.
- Java 21.0.11.
- Flyway conectado a Neon `ep-blue-wind-acaugpzx-pooler.sa-east-1.aws.neon.tech`.
- Netty escuchando en el puerto 10000.
- Tiempo de arranque Spring: **106.607 s**.
- El panel de Render informa que el plan Free puede demorar 50 s o más al despertar.

Esto demuestra que el arranque es materialmente lento, pero no sustituye un drill de suspensión y reactivación medido desde una pestaña nueva.

## Health público

Se tomaron 10 muestras consecutivas de cada endpoint:

| Endpoint | N | p50 | p95 | máximo | Estados |
|---|---:|---:|---:|---:|---|
| `/api/v1/health/liveness` | 10 | 228 ms | 258 ms | 800 ms | 10×200 |
| `/api/v1/health/readiness` | 10 | 776 ms | 2.062 ms | 2.259 ms | 9×200, 1×503 |

En una consulta posterior aislada, readiness respondió `503` con `database: DOWN` y `redis: UP`; el reintento inmediato respondió `200` con ambos servicios `UP`. La intermitencia impide marcar readiness como estable para testers externos.

## Partido preparado para restart

En una carrera pública nueva se inició la fecha 2 y se observó antes de cualquier restart:

- estado: `RUNNING`;
- minuto: 23;
- marcador: 0-1;
- eventos: 5;
- round y match IDs registrados en la sesión, sin exponer credenciales.

El control de Render no permitió completar un restart verificable desde la sesión existente: la interacción abrió un artefacto inexistente y no generó una nueva revisión live. Por eso no se afirma qué quedó en memoria, Redis o PostgreSQL.

## Resultado del drill

| Gate | Estado |
|---|---|
| Dormir el servicio según plan Free | NO EJECUTADO DE FORMA CONTROLADA |
| Medir primera llamada tras cold start | NO CERTIFICADO |
| Reiniciar durante partido `RUNNING` | NO EJECUTADO |
| Consultar estado después del restart | NO CERTIFICADO |
| Clasificar estado perdido/recuperado | NO CERTIFICADO |
| Verificar que la UI no invente 0-0 | NO CERTIFICADO |

## Contrato pendiente

Hasta completar el drill, el sistema no puede prometer persistencia completa de una `LiveSession` durante reinicios. El comportamiento seguro requerido es mostrar estado no disponible/reintento controlado, nunca sintetizar un resultado final.

## Verificación posterior al redeploy

Tras publicar el frontend corregido desde `dfed971`, se repitieron diez muestras warm contra los endpoints públicos: liveness `10×200`, p50 `256 ms`, p95 `260 ms`, máximo `372 ms`; readiness `10×200`, p50 `747 ms`, p95 `791 ms`, máximo `793 ms`. Estas muestras no se presentan como cold start ni reemplazan el restart durante un partido.

## Conclusión

El startup lento y la readiness intermitente son evidencia real de riesgo operativo. Falta el restart controlado y su recuperación observable; el gate permanece **REJECTED**.
