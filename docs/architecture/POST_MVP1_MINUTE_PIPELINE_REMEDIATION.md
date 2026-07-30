# Post-MVP 1 minute pipeline remediation

Fecha: 2026-07-30

## Alcance

Esta remediacion cierra los hallazgos del informe `POST_MVP1_DETAILED_MATCH_CORE_FINAL_INDEPENDENT_AUDIT.md` sobre el pipeline minuto a minuto del partido detallado.

No cambia contratos publicos, API, frontend, base de datos, datasets ni comportamiento funcional esperado. El objetivo fue convertir el procesamiento de minuto en un pipeline profesional, observable, deterministico y testeable.

## Problemas de partida

La auditoria historica registro estos puntos:

- `DetailedMatchMinuteFlow.processMinute` tenia 201 lineas y mezclaba sustituciones, tactica, posesion, ataque, disciplina, fisico y sustituciones automaticas.
- `DetailedMatchMinuteSupport` agrupaba dependencias heterogeneas como holder tecnico.
- `MinuteSimulationContext` mezclaba configuracion estable, estado mutable, RNG, timeline y resultado.
- Existia composicion manual escondida.
- Faltaban pruebas golden de equivalencia deterministica.
- `DetailedMatchEngineFlow` conservaba estado global estatico para contabilizar goles agregados.

## Orden funcional preservado

El orden original del minuto fue conservado:

1. Sustituciones manuales programadas.
2. Calculo de pasadores y slots efectivos.
3. Calculo tactico de shapes y posesion base.
4. Tirada de posesion.
5. Tick de posesion.
6. Desgaste fisico de ambos equipos.
7. Seleccion de amenaza ofensiva y agregados tacticos.
8. Probabilidad de chance.
9. Intento de remate.
10. Registro de chance creada.
11. Disciplina: falta, amarilla, segunda amarilla y roja.
12. Lesion.
13. Corner.
14. Offside.
15. Sustituciones automaticas desde el minuto 60.

## Diseno final

El flujo quedo dividido en fases explicitas:

- `MinuteScheduledSubstitutionPhase`
- `MinuteTacticalStatePhase`
- `MinutePossessionPhase`
- `MinuteAttackPhase`
- `MinuteDisciplinePhase`
- `MinutePhysicalStatePhase`
- `MinuteAutomaticSubstitutionPhase`

`DetailedMatchMinuteFlow` ya no contiene la logica del minuto: delega en `DetailedMatchMinutePipeline`, que conserva el orden funcional. La composicion se centraliza en `DetailedMatchMinuteComposition` y las dependencias se agrupan por capacidades reales:

- `MinuteTacticalPolicies`
- `MinuteEventPolicies`
- `MinutePlayerStatePolicies`
- `MinuteSubstitutionPolicies`

`DetailedMatchMinuteSupport` fue eliminado.

## Estado, configuracion y resultado

`MinuteSimulationContext` fue eliminado y reemplazado por objetos con responsabilidades separadas:

- `MinuteSimulationConfig`: datos estables de simulacion.
- `MinuteMatchState`: estado mutable de ejecucion del partido.
- `MinuteSimulationInput`: entrada explicita del minuto.
- `MinuteSimulationResult`: resultado observable del minuto.

Los constructores validan nulidad, rango de minuto, valores finitos y estados local/visitante distintos.

## Determinismo y concurrencia

Se agrego `DetailedMatchMinuteGoldenSnapshotTest` con escenarios golden:

- `balanced-42`
- `favorite-7`
- `defensive-99`
- `manual-sub-12345`

Cada snapshot cubre marcador, remates, remates al arco, xG, posesion, cantidad de eventos, goles, minutos de gol, chances, corners, offsides, faltas, amarillas, rojas, lesiones, sustituciones, hash de timeline y eventos de cabecera/cola.

Tambien se agrego una prueba de concurrencia que compara ejecuciones secuenciales y paralelas con seeds independientes. La eliminacion de `goalAdditions` evita estado compartido entre partidos.

## Guardrails de arquitectura

`SimulationArchitectureBoundaryTest` protege:

- ausencia de nombres genericos `Support`, `Helper` y `Utils` en simulacion detallada;
- fases minuto sin dependencias web, persistencia, infraestructura ni Spring;
- objetos de config/state/input/result sin adapters, infraestructura ni Spring;
- flows sin campos estaticos mutables;
- metodos del pipeline/fases por debajo del umbral de longitud definido.

## Metricas resultantes

| Pieza | Lineas | Responsabilidad |
| --- | ---: | --- |
| `DetailedMatchMinuteFlow` | 44 | Fachada del flujo minuto |
| `DetailedMatchMinutePipeline` | 65 | Orden de fases |
| `MinuteAttackPhase` | 156 | Resolucion ofensiva del minuto |
| `MinuteSimulationInput` | 66 | Entrada explicita del minuto |
| `MinuteSimulationConfig` | 23 | Configuracion estable |
| `MinuteMatchState` | 30 | Estado mutable del partido |
| `MinuteSimulationResult` | 11 | Resultado observable |

`MinuteAttackPhase` queda como la clase de fase mas grande, pero su responsabilidad es unica y las reglas de arquitectura verifican metodos acotados.

## Validacion

Validaciones ejecutadas:

- Compilacion de tests: verde.
- Tests focalizados de simulacion detallada: verde.
- Golden snapshots: verde.
- Concurrencia/determinismo: verde.
- Arquitectura: verde.
- Suite backend completa: 2472 tests, 0 failures, 0 errors, 4 skipped.

El frontend no fue modificado y su estado Git permanece limpio.

## Commits

- `81423903 Characterize detailed match minute behavior`
- `7e8bdcd9 Extract detailed match minute pipeline phases`
- `8ffd2532 Remove minute support dependency holder`
- `d9365a15 Separate minute configuration state and result`
- `0035762f Prove detailed match determinism and concurrency`

## Resultado

La remediacion del pipeline minuto a minuto queda cerrada profesionalmente. No quedan hallazgos criticos ni importantes dentro de este alcance.

Veredicto tecnico: `APPROVED`.
