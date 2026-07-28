# Backend refactor y limpieza - primera pasada jugable

Fecha: 2026-07-27
Repo: `D:\ProyectosOpenCode\MANAGER`
Branch: `feat/v25d99.20.3.1-runtime-fixes`

## Objetivo

Dejar el backend más limpio para sostener el MVP jugable sin cambiar comportamiento del motor, harness ni lineup. Esta pasada prioriza seguridad: extraer helpers pequeños, separar controllers por responsabilidad y quitar comentarios de parche que hacían que el código pareciera temporal.

## Cambios hechos

### Harness

- `TestHarnessController` quedó como controller principal de creación, snapshot, replay y matrices.
- Se creó `TestHarnessLabsController` para endpoints `/labs/...` y `/match/{matchId}/labs/...`.
- Se creó `TestHarnessPreviewRunner` para sacar del use case la simulación multi-seed de preview.
- Se corrigió la inyección de `TestHarnessUseCaseImpl` marcando el constructor principal con `@Autowired`, porque al mantener un constructor de compatibilidad para tests Spring podía intentar instanciar sin constructor por defecto.

### Motor V24 / live

- `V24ShotXgCalculator` delega multiplicadores de skills en `V24ShotSkillMultipliers`.
- `V24LiveSession` quedó debajo de 500 líneas después de quitar documentación vieja incrustada.
- `V24MatchContext` y `V24MatchContextFactory` quedaron debajo de 500 líneas después de limpiar documentación de parche.
- `V24DetailedMatchEngine` bajó de 2780 a 1741 líneas quitando ruido, pero sigue siendo God Class real.

### Ratings/formaciones

- `TeamRatingsCalculator` delega bases de formaciones en `FormationRatingBases`.
- `TeamRatingsCalculator` quedó debajo de 500 líneas.

### Controllers

- `RoundController` quedó debajo de 500 líneas.
- `TestHarnessController` quedó debajo de 500 líneas.

## Tests corridos y estado

Verdes:

- Compilación backend desde limpio: `mvn -q clean test-compile`
- Controllers/round engine: `GameControllerV25D79Test`, `GameControllerE2ETest`, `RoundEngine*Test`, `RoundEngineV25D87SseWireupTest`, `RoundEngineSchedulerSurvivesExceptionTest`
- Harness/live: `TestHarnessUseCaseImplTest`, `TestHarnessFormationMatrixSlotAssignmentTest`, `TestHarnessController*Test`, `V24LiveSession*Test`, `V24SubstitutionEngineTest`
- Motor/lineup/formaciones: `V24DetailedMatchEngine*Test`, `V24ShotXgCalculator*Test`, `V24MatchContext*Test`, `LeagueSimulator*Test`, `Lineup*Test`, `Formation*Test`

## Estado de clases grandes después de esta pasada

Todavía requieren refactor real por módulos:

| Clase | Líneas aprox. | Estado |
|---|---:|---|
| `TestHarnessUseCaseImpl` | 6270 | God Class principal. Mezcla labs, matrices, escenarios, swaps, pixels y helpers. Próximo corte recomendado. |
| `V24DetailedMatchEngine` | 1741 | God Class de motor. Hay que separar posesión, generación de chances, resolución de tiros/eventos y agregación estadística. |
| `LineupCommandUseCaseImpl` | 1188 | Mezcla selección automática, selección manual, slots, warnings y armado DTO. Conviene separar asignador de slots y builder DTO. |
| `LeagueSimulator` | 871 | Orquestador de ronda con persistencia, V23/V24 fallback, detalle y mutaciones. Conviene separar persistencia de detalle y mutaciones. |
| `TestHarnessUseCase` | 757 | Interfaz/contrato enorme por records del harness. Conviene mover records DTO del harness a archivos propios. |

## Próximo paso recomendado

No tocar todavía lesiones/stamina nueva. Antes conviene cerrar limpieza backend en orden:

1. Extraer `TestHarnessUseCaseImpl` en servicios por familia:
   - `TestHarnessLabMutationService`
   - `FormationMatrixRunner`
   - `ScenarioMatrixRunner`
   - `PlayerSwapMatrixRunner`
   - `PositionPixelMatrixRunner`
2. Extraer `LineupCommandUseCaseImpl`:
   - `LineupSlotAssignmentService`
   - `LineupDtoAssembler`
   - `LineupValidationService`
3. Extraer `V24DetailedMatchEngine`:
   - posesión/canal ofensivo
   - creación de chances
   - resolución de tiros/xG/eventos
   - timeline/stats accumulator
4. Volver a correr smoke backend + harness visual.
5. Commit/push solo cuando todo quede verde.

## Nota importante

Esta pasada deja mejor el backend, pero no significa “100% sin God Classes”. La deuda restante está localizada y testeada; el siguiente trabajo debe ser refactor por responsabilidad, no solo borrar líneas.
