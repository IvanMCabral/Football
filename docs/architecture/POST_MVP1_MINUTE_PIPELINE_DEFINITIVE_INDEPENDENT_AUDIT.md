# MANAGER - Auditoria independiente definitiva del pipeline minuto a minuto

Fecha: 2026-07-30

## 1. Veredicto

APPROVED WITH ISSUES

La remediacion elimina la god method anterior de 201 lineas y deja un pipeline minuto a minuto legible, con fases explicitas, sin `DetailedMatchMinuteSupport`, sin estado global mutable funcional, con golden snapshots utiles, test concurrente real, ArchUnit reforzado, `git diff --check` limpio y suite backend completa verde.

No corresponde `APPROVED` puro porque aun quedan issues relevantes: `MinutePhysicalStatePhase` mezcla lesiones, corners y offsides en una misma fase, no hay tests unitarios especificos por cada fase del pipeline, los golden snapshots son buenos pero no cubren ratings/fatiga de forma directa, y la composicion sigue siendo manual con un record `DetailedMatchMinuteComposition` que agrupa policies y servicios de bajo nivel.

## 2. Commits

Commits auditados:

- `81423903 Characterize detailed match minute behavior`
- `7e8bdcd9 Extract detailed match minute pipeline phases`
- `8ffd2532 Remove minute support dependency holder`
- `d9365a15 Separate minute configuration state and result`
- `0035762f Prove detailed match determinism and concurrency`
- `28de2f2c Close detailed match minute pipeline remediation`

Base contrastada:

- `19b33497 Close detailed match core quality remediation`

## 3. Git

Estado inicial:

- `git status --short`: limpio.
- Frontend `D:\ProyectosOpenCode\MANAGER\front-ciber\project`: limpio.
- `git diff --check 19b33497..28de2f2c`: sin salida.

Cambios del rango:

- Agrega documentos de remediacion y auditoria del pipeline.
- Modifica `DetailedMatchEngineFlow`.
- Agrega `DetailedMatchMinuteComposition`.
- Reduce `DetailedMatchMinuteFlow`.
- Agrega `DetailedMatchMinutePipeline`.
- Elimina `DetailedMatchMinuteSupport`.
- Modifica `MatchResultFinalizer` y `ShotAttemptService`.
- Agrega fases y modelos de minuto.
- Modifica `SimulationArchitectureBoundaryTest`.
- Agrega `DetailedMatchMinuteGoldenSnapshotTest`.

No se detectaron cambios en frontend, DB, Redis ni dataset.

## 4. Inventario

Clases principales del nuevo pipeline:

- `DetailedMatchEngine`: fachada publica del motor detallado.
- `DetailedMatchEngineFlow`: flujo global del partido.
- `DetailedMatchMinuteFlow`: composicion del pipeline del minuto y delegacion.
- `DetailedMatchMinutePipeline`: coordinador ordenado de fases.
- `DetailedMatchMinuteComposition`: factory/composicion de policies.
- `MinuteScheduledSubstitutionPhase`: aplica sustituciones manuales agendadas.
- `MinuteTacticalStatePhase`: calcula slots efectivos, shape tactico y share de posesion.
- `MinutePossessionPhase`: decide posesion y aplica drain base por minuto.
- `MinuteAttackPhase`: calcula amenaza, probabilidad ofensiva, tiros y chance created.
- `MinuteDisciplinePhase`: faltas, amarillas y segunda amarilla roja.
- `MinutePhysicalStatePhase`: lesiones, corners y offsides.
- `MinuteAutomaticSubstitutionPhase`: sustituciones automaticas desde minuto 60.
- `MinuteSimulationConfig`: configuracion inmutable por partido.
- `MinuteMatchState`: estado mutable compartido por el minuto.
- `MinuteSimulationInput`: input por minuto.
- `MinuteSimulationResult`: resumen observable de eventos agregados.
- Records de policies: `MinuteTacticalPolicies`, `MinuteEventPolicies`, `MinutePlayerStatePolicies`, `MinuteSubstitutionPolicies`.
- Records de estado intermedio: `MinuteTacticalState`, `MinutePossessionState`, `MinuteAttackState`.

## 5. Metricas

| Clase | Lineas | Metodos aprox. | Campos | Mutable | Fan-out aprox. | Max parametros | Metodo mas largo |
|---|---:|---:|---:|---:|---:|---:|---|
| `DetailedMatchEngine` | 95 | 9 | 1 | 0 | 10 | 4 | 3 |
| `DetailedMatchEngineFlow` | 206 | 18 | 6 | 0 | 26 | 3 | 22 |
| `DetailedMatchMinuteFlow` | 44 | 4 | 1 | 0 | 13 | 4 | 12 |
| `DetailedMatchMinutePipeline` | 65 | 3 | 7 | 0 | 5 | 7 | 16 |
| `DetailedMatchMinuteComposition` | 47 | record/factory | 4 components | 0 | 22 | 2 | create ~34 |
| `MinuteAttackPhase` | 156 | 12 | 3 | 0 | 4 | 3 | 23 |
| `MinuteAutomaticSubstitutionPhase` | 29 | 2 | 1 | 0 | 0 | 2 | 19 |
| `MinuteDisciplinePhase` | 49 | 3 | 2 | 0 | 1 | 2 | 29 |
| `MinutePhysicalStatePhase` | 65 | 7 | 1 | 0 | 2 | 2 | 53 |
| `MinutePossessionPhase` | 43 | 5 | 1 | 0 | 1 | 2 | 24 |
| `MinuteScheduledSubstitutionPhase` | 38 | 5 | 1 | 0 | 1 | 1 | 26 |
| `MinuteTacticalStatePhase` | 55 | 2 | 3 | 0 | 4 | 1 | 30 |
| `MinuteSimulationConfig` | 23 | 2 | record | 0 | 2 | 2 | 5 |
| `MinuteMatchState` | 30 | record | 8 components | referencias mutables | 4 | 8 | 3 |
| `MinuteSimulationInput` | 66 | 13 | record | 0 directo | 3 | 3 | 3 |
| `MinuteSimulationResult` | 11 | 1 | record | 0 | 0 | 3 | 3 |
| `ShotAttemptService` | 234 | 14 | 7 | 0 | 9 | 7 | 30 |

La god method anterior fue resuelta. El metodo mas largo actual es `MinutePhysicalStatePhase.apply`, con 53 lineas.

## 6. Coordinador

`DetailedMatchMinuteFlow` ya no contiene reglas de posesion, probabilidad, disparos, faltas, tarjetas, lesiones, sustituciones, shape o eventos. Construye el pipeline y delega.

`DetailedMatchMinutePipeline` recibe `MinuteSimulationInput`, ejecuta fases en orden y devuelve `MinuteSimulationResult`.

Orden real:

1. `MinuteScheduledSubstitutionPhase`
2. `MinuteTacticalStatePhase`
3. `MinutePossessionPhase`
4. `MinuteAttackPhase`
5. `MinuteDisciplinePhase`
6. `MinutePhysicalStatePhase`
7. `MinuteAutomaticSubstitutionPhase`

Resultado: aprobado.

## 7. Fases

### MinuteScheduledSubstitutionPhase

- Entrada: `MinuteSimulationInput`.
- Salida: muta timeline, team state y set de sustituciones aplicadas.
- Responsabilidad: sustituciones manuales que vencen en el minuto actual.
- Eventos: `SUBSTITUTION`.
- Cohesion: buena.

### MinuteTacticalStatePhase

- Entrada: `MinuteSimulationInput`.
- Salida: `MinuteTacticalState`.
- Responsabilidad: slots efectivos, shapes, passers y share de posesion.
- Eventos: ninguno.
- Cohesion: buena.

### MinutePossessionPhase

- Entrada: input + tactical state.
- Salida: `MinutePossessionState`.
- Responsabilidad: determinar posesion, seleccionar equipos/selector y aplicar fatiga base.
- Eventos: ninguno.
- Cohesion: aceptable, aunque mezcla decision de posesion con drain base.

### MinuteAttackPhase

- Entrada: input + possession state.
- Salida: `MinuteAttackState`.
- Responsabilidad: amenaza ofensiva, probabilidad, tiros y chance created.
- Eventos: tiros, bloqueos, goles a traves de `ShotAttemptService`, y `CHANCE_CREATED`.
- Cohesion: buena para fase ofensiva.

### MinuteDisciplinePhase

- Entrada: input + possession state.
- Salida: muta timeline/player state.
- Responsabilidad: faltas, amarillas y segunda amarilla roja.
- Eventos: `FOUL`, `YELLOW_CARD`, `RED_CARD`.
- Cohesion: buena.

### MinutePhysicalStatePhase

- Entrada: input + possession state.
- Salida: muta timeline/player state.
- Responsabilidad actual: lesiones, corners y offsides.
- Eventos: `INJURY`, `CORNER`, `OFFSIDE`.
- Cohesion: issue importante. Lesion es estado fisico, pero corner/offside son eventos de juego, no estado fisico. Esta fase tiene multiples razones de cambio.

### MinuteAutomaticSubstitutionPhase

- Entrada: input + possession state.
- Salida: muta timeline/team state.
- Responsabilidad: sustituciones automaticas desde minuto 60.
- Eventos: `SUBSTITUTION`.
- Cohesion: buena.

## 8. Context/config/state/result

Separacion actual:

- `MinuteSimulationConfig`: configuracion inmutable del partido, bases de posesion e intensidad. Valida null y finitud.
- `MinuteMatchState`: estado mutable del partido: RNG, equipos, timeline, selectors, set de sustituciones y engine de sustitucion agendada.
- `MinuteSimulationInput`: une config, state y minuto; valida rango 1-90.
- `MinuteSimulationResult`: minuto y cantidad de eventos antes/despues.

No hay adapters, Spring, Redis o persistencia dentro de estos modelos.

Issues:

- `MinuteMatchState` aun contiene comportamiento (`SubstitutionEngine`) junto con estado.
- `MinuteMatchState` contiene varias referencias mutables. Esta acotado al partido, pero requiere disciplina de ownership.
- `MinuteSimulationResult` es muy minimo; no expresa tipos de efectos, solo conteo.

## 9. Composicion

`DetailedMatchMinuteComposition.create` crea services de bajo nivel y los agrupa en records de policies. Luego `DetailedMatchMinuteFlow` construye las fases con esas policies.

Fortalezas:

- Orden de fases visible.
- Sin Spring innecesario.
- Sin service locator.
- Sin `Support`, `Helper` o `Utils`.

Issues:

- Composicion manual con muchos `new`.
- `DetailedMatchMinuteComposition` tiene responsabilidad tecnica de factory/composition, no de negocio.
- Las fases no se sustituyen desde afuera salvo por constructores package-private y tests del paquete.

Resultado: aprobado con issues menores.

## 10. Estado global

`goalAdditions` estatico fue eliminado del core del minuto.

Busqueda del core detallado:

- No se detecto `AtomicInteger` nuevo en el pipeline.
- No se detectaron campos static mutables en flows del paquete de simulacion.
- Persisten caches/listas en clases historicas como `CachingRandomWrapper`, pero no fueron introducidas por este cierre.

Resultado: aprobado.

## 11. Determinismo

`DetailedMatchMinuteGoldenSnapshotTest` agrega snapshots por seed y escenario.

Escenarios cubiertos:

- Partido equilibrado.
- Favorito fuerte.
- Equipo defensivo.
- Sustitucion manual.

Datos congelados:

- Marcador.
- Tiros.
- On target/save count.
- xG.
- Posesion.
- Cantidad de eventos.
- Goles y minutos de gol.
- Chances.
- Corners.
- Offsides.
- Fouls.
- Amarillas.
- Rojas.
- Lesiones.
- Sustituciones.
- Hash de timeline.
- Head/tail del timeline.

No se observaron cambios de expected values en tests existentes para esconder regresiones; se agrego un test nuevo con expected completos.

Resultado: aprobado.

## 12. Golden snapshots

Los snapshots son legibles y relativamente completos. Cubren mas que marcador/count de eventos.

Limitaciones:

- No congelan ratings ni fatiga por jugador de forma directa.
- No hay escenario especifico forzado de expulsion como input, aunque el escenario manual-sub produce una roja.
- El timeline completo se protege por hash, pero solo head/tail son legibles.

Resultado: aprobado con issue menor.

## 13. Concurrencia

`concurrentMatchesKeepIndependentDeterministicState` ejecuta cuatro seeds en un `ExecutorService` de cuatro threads y compara contra resultados secuenciales.

Fortalezas:

- Es paralelismo real.
- Usa seeds distintos.
- Compara snapshots completos por seed.
- Verifica independencia observable de timeline/resultado.

Limitaciones:

- No repite muchas rondas del mismo test.
- No prueba seeds iguales en paralelo.

Resultado: aprobado con issue menor.

## 14. Orden

El orden preservado por el pipeline es:

1. sustituciones manuales;
2. tacticas/slots/shape;
3. posesion y fatiga base;
4. ataque/disparo/chance;
5. disciplina;
6. lesiones/corners/offsides;
7. sustituciones automaticas.

El golden snapshot protege el orden de consumo de RNG y el timeline observable. No hay evidencia de cambio funcional accidental.

Observacion: al estar protegido por snapshots agregados despues del refactor, no prueba contra un golden previo versionado anterior a la extraccion; prueba estabilidad del estado actual.

## 15. Arquitectura

`SimulationArchitectureBoundaryTest` ahora incluye:

- Simulation core no depende de adapters/infrastructure/Redis/JDBC/R2DBC/SQL.
- Detailed flow components no dependen de web/persistence.
- Orchestrators permanecen en application layer.
- Flows con maximo de campos.
- Prohibicion de nombres `Support`, `Helper`, `Utils`.
- Fases sin web/persistence/infrastructure/Spring/SQL.
- Context/config/state/input/result sin adapters/infrastructure/Spring.
- Flows sin campos static mutables.
- Check textual de metodo largo en archivos centrales con limite 80.

Debilidades:

- El guard de metodo largo es textual y no parser Java completo.
- No mide cohesion semantica; `MinutePhysicalStatePhase` pasa pese a mezclar eventos heterogeneos.
- Puede eludirse con una clase fuera del patron `DetailedMatchMinute.*` o `Minute.*Phase`.

Resultado: aprobado con issues menores.

## 16. Tests

Validaciones ejecutadas:

- `mvn -q -DskipTests test-compile`: verde.
- Focalizados: arquitectura, golden/concurrencia, engine, formaciones, sustituciones, disciplina, lesiones, LeagueSimulator, Redis y runtime: verde.
- `mvn -q test`: verde.

Conteo Surefire:

- Tests: 2472
- Failures: 0
- Errors: 0
- Skipped: 4
- Reportes: 266

Delta contra 2466:

- +6 tests netos.
- Explicacion: nuevo `DetailedMatchMinuteGoldenSnapshotTest` aporta 2 tests y `SimulationArchitectureBoundaryTest` agrega reglas/checks adicionales que Surefire reporta dentro del total.
- No se detectaron nuevos skips.
- No se detectaron clases de test eliminadas en el rango.

Calidad:

- Buenos tests golden de comportamiento observable.
- Buen test concurrente de independencia observable.
- Falta test unitario propio por cada fase.
- No se detecto reflection en tests auditados.

## 17. Higiene

Evidencia:

- `git diff --check 19b33497..28de2f2c`: limpio.
- `git diff --check` del worktree luego de crear este informe: pendiente de verificacion final externa al contenido del informe.
- Frontend intacto.
- `DetailedMatchMinuteSupport` eliminado.
- No se detectaron `V23`/`V24` en el nuevo pipeline.
- No se detectaron nombres `Helper` o `Utils` en el nuevo pipeline.

Issue:

- El nombre `DetailedMatchMinuteComposition` es tecnico, pero no llega a ser helper generico encubierto.

## 18. Documentacion

Documentos contrastados:

- `POST_MVP1_MINUTE_PIPELINE_REMEDIATION.md`
- `POST_MVP1_MINUTE_PIPELINE_FINAL_INDEPENDENT_AUDIT.md`
- `POST_MVP1_DETAILED_MATCH_CORE_FINAL_INDEPENDENT_AUDIT.md`

La documentacion declara `APPROVED` y reporta correctamente 2472/0/0/4, soporte eliminado, pipeline por fases, golden y concurrencia.

Issue: el `APPROVED` omite matices importantes: `MinutePhysicalStatePhase` mezcla lesiones/corners/offsides, no hay tests especificos por fase, y los snapshots no cubren ratings/fatiga directamente.

## 19. Criticos

Ninguno.

No hay suite roja, no hay cambio de frontend, no se detecta nuevo estado global mutable funcional, no hay Support residual y no hay persistencia/web/Redis dentro del pipeline de minuto.

## 20. Importantes

1. `MinutePhysicalStatePhase` no es completamente cohesionada: mezcla lesion con corner y offside.
2. No hay tests unitarios especificos por cada fase; el comportamiento esta protegido principalmente por golden snapshots end-to-end.
3. `MinuteMatchState` mantiene referencias mutables y un `SubstitutionEngine` dentro del estado del minuto.

## 21. Menores

1. `DetailedMatchMinuteComposition` es una factory tecnica con muchos `new`; aceptable, pero no ideal.
2. Golden snapshots no verifican ratings/fatiga de forma directa.
3. El test de concurrencia no repite muchas rondas ni prueba seeds iguales en paralelo.
4. El guard de metodo largo usa lectura textual, no AST/parser Java.

## 22. Preparacion para features

Preparado para continuar features: si.

Reglas recomendadas antes de sumar mas mecanicas al partido:

- Separar `MinutePhysicalStatePhase` en `MinuteInjuryPhase` y `MinuteSetPieceOrGameEventPhase` o nombres equivalentes.
- Agregar tests unitarios por fase.
- Extender golden snapshots para ratings/fatiga si esas metricas seran sensibles para gameplay.
- Mantener el limite de metodo largo y reforzarlo con una herramienta mas robusta si el pipeline crece.

## 23. Conclusion

La remediacion resuelve el problema principal de la auditoria anterior: ya no hay una god method de 201 lineas en el minuto. El pipeline es visible, testeado y determinista. El estado actual es apto para seguir desarrollando features, pero no alcanza un `APPROVED` sin matices porque aun quedan pequenas concentraciones semanticas y faltan tests unitarios por fase.

## 24. Final cohesion remediation status

Estado actualizado: los issues residuales de este informe fueron corregidos en
la remediacion final del 2026-07-30.

Evidencia:

- `MinutePhysicalStatePhase` fue eliminado.
- `MinuteInjuryPhase` concentra exclusivamente lesiones.
- `MinuteRestartEventPhase` concentra corners y offsides.
- `MinuteMatchState` ya no contiene `SubstitutionEngine` ni otros servicios de
  comportamiento.
- Todas las fases del minuto tienen tests unitarios especificos.
- El golden snapshot cubre distribucion de eventos por jugador, proxy de rating,
  proyeccion de fatiga, hash de timeline y concurrencia con seeds repetidas.
- `SimulationArchitectureBoundaryTest` impide reintroducir el aggregate
  physical-state phase y evita que state/input/result dependan de composition,
  policies, engines o phases.

La auditoria final independiente de esta correccion queda registrada en:

- `docs/architecture/POST_MVP1_MINUTE_PIPELINE_FINAL_COHESION_AUDIT.md`

Veredicto final de la remediacion posterior: `APPROVED`.
