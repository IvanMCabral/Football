# Naming consolidation plan

Fecha: 2026-07-28

Este documento inventaría nombres temporales o técnicos que todavía existen y propone cómo consolidarlos en una fase futura. En esta fase no se renombran clases porque el cierre de producción pidió inventario, no cambio de contratos/nombres.

## Criterio

- Mantener nombres versionados solo cuando representan compatibilidad real o selección de motor.
- Renombrar nombres activos a lenguaje de dominio cuando V24/V23 ya no sean experimentos.
- Evitar `Helper` cuando la clase tenga una responsabilidad nombrable.
- Mantener `Impl` solo cuando sea una implementación explícita de un puerto/use case y no esconda una responsabilidad difusa.

## Inventario prioritario

| Señal | Ubicaciones principales | Riesgo | Consolidación propuesta |
|---|---|---:|---|
| `V24` | `application/service/simulation/v24/*`, `V24DetailedMatchController`, tests de motor/harness/stats | Medio | Cuando el motor detallado sea el camino estable, renombrar a `detailedmatch/*`, `DetailedMatchEngine`, `LiveMatchSession`, `DetailedMatchStoragePort`, `DetailedMatchController`. |
| `V23` | `MatchEngineImpl`, configuración de simulación, tests de calidad V23, docs archivados | Bajo/medio | Mantener solo donde exista selección real del motor anterior; si queda como default legacy, aislar bajo `legacy` o `classicmatch`. |
| `Legacy` | comentarios de compatibilidad en lineup, match session, controllers y docs archivados | Bajo | Reescribir comentarios activos a lenguaje funcional: “historic save format”, “classic match path”, “backward-compatible read”. |
| `MVP` | comentarios y docs de lineup/test harness/stats | Bajo | Eliminar de código activo al tocar cada área; reemplazar por explicación estable del comportamiento. |
| `compat` / `backward compat` | DTOs de lineup, snapshots, query services, docs | Bajo | Mantener solo en adaptadores de entrada/salida o migración de saves; evitarlo en dominio salvo que describa invariantes de serialización. |
| `Helper` | `ControllerHelper`, `LineupHelper`, `FixtureQueryHelper`, servicios/test support | Medio | Renombrar por responsabilidad concreta: `LineupValidator`, `LineupSlotResolver`, `FixtureQueryAssembler`, `ControllerResponseFactory`, etc. |
| `Impl` | use cases y servicios de aplicación | Bajo/medio | Aceptable para implementaciones de puertos. Si una clase `Impl` concentra reglas múltiples, dividir por capability antes de renombrar. |

## Archivos activos a revisar primero

| Prioridad | Archivo/área | Motivo |
|---:|---|---|
| 1 | `src/main/java/com/footballmanager/application/service/simulation/v24/` | Es el motor principal detallado; `V24` ya comunica historia interna más que dominio. |
| 2 | `src/main/java/com/footballmanager/application/service/lineup/LineupHelper.java` | `Helper` oculta validación, inferencia y normalización de lineup. |
| 3 | `src/main/java/com/footballmanager/application/service/query/FixtureQueryHelper.java` | `Helper` puede dividirse en assembler/resolver si vuelve a crecer. |
| 4 | `src/main/java/com/footballmanager/adapters/in/web/common/ControllerHelper.java` | Nombre genérico aceptable a corto plazo, pero debería expresar construcción de respuestas/autenticación si amplía alcance. |
| 5 | `src/main/java/com/footballmanager/application/service/domain/MatchEngineImpl.java` | `Impl` y `V23` en comentarios indican que el motor clásico debería tener nombre estable. |

## Regla para ejecutar la consolidación

1. Renombrar una familia por vez.
2. Mantener compatibilidad externa de endpoints y JSON.
3. Ejecutar suite focalizada de la familia renombrada.
4. Ejecutar suite completa antes de commit.
5. Actualizar docs y mapa mental en el mismo cambio.
