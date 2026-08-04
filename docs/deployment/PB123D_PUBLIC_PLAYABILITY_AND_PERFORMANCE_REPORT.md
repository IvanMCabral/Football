# PB1.2.3D — Public playability and performance report

Fecha de ejecución: 2026-08-04  
Rama: `feat/v25d99.20.3.1-runtime-fixes`

## Alcance

Esta certificación cubre el registro público, las rutas de carrera, la edición táctica y la latencia observable del backend desplegado en Render (`https://manager-staging-api.onrender.com`) y del frontend publicado en Firebase (`https://manager-4f952.web.app`). No se consideran certificados por inferencia los flujos que no fueron ejecutados de punta a punta.

## Registro seguro

El alta sin `username` dejó de alcanzar la persistencia. El backend valida email, username y password antes de guardar y convierte una colisión de integridad en un error de conflicto controlado. El contrato observado es `422 AUTH_VALIDATION_ERROR` con `requestId` para una solicitud incompleta; no se expone SQL, nombres de tablas ni mensajes del driver. El frontend valida username (3–50 caracteres alfanuméricos, `_`, `-`, `.`) y password (8–128) antes de enviar.

## Latencia observable

Las mediciones previas de health sobre la revisión pública fueron:

| Operación | Muestras | p50 | p95 | Máximo | Estado |
|---|---:|---:|---:|---:|---|
| liveness (warm) | 10 | 211 ms | 666 ms | 690 ms | 200 en todas |
| readiness (warm) | 10 | 776 ms | 1003 ms | 13740 ms | 200 en todas |

La primera carga de Spring observada anteriormente fue de aproximadamente 106,6 s; el plan gratuito de Render puede suspender instancias. Esa cifra sigue siendo un riesgo operativo, no un objetivo de rendimiento cumplido.

## Instrumentación incorporada

`RuntimeOperationMetrics` mide duración, éxitos y errores con publishers diferidos, sin registrar claves Redis, payloads, tokens, contraseñas ni datos personales. Se instrumentaron operaciones `redis.career.*`, `postgres.user.*`, `http.dashboard.*` y `http.career.*`. Los logs agregados se emiten cada diez observaciones o ante error. La instrumentación es segura y útil para comparar revisiones, pero no sustituye todavía una métrica externa persistente de payload/TTL ni un dashboard de producción.

## Estado de certificación

- Registro/login: corregido y cubierto por pruebas focalizadas.
- CORS, health y publicación SPA: evidencia pública previa vigente.
- Operación cálida: medida; la readiness presenta outlier de cold/wake-up.
- Optimización: se agregaron mediciones sin alterar reglas de juego.
- Tactical editor: la interacción de arrastre y cambio de formación tiene evidencia de escritorio; swap de titulares/banco, confirmación tras recarga y validación móvil no quedaron certificados completamente en esta ejecución.
- Full season: no se presenta evidencia reproducible de una temporada completa más transición y ronda 1 de la segunda temporada.

## Veredicto

`REJECTED` para cierre PB1.2.3D. El defecto de registro y la observabilidad básica están resueltos, pero faltan gates obligatorios de jugabilidad de temporada completa y certificación táctica integral. No se inventan resultados para esos escenarios.
