# V24D8-BUG-003 — Fix: Squad queda vacío post-career/start

**Fecha:** 2026-06-14
**Branch:** master
**Commits:** pending (test agregado, código fuente sin cambios)
**Tests:** 932 PASS, 0 fail, 0 error

---

## Resumen ejecutivo

El bug reportaba `userSessionTeamId: null`, `userTeamId: null` y `squadSize: 0` después de crear una career con La Liga + Real Madrid en usuario fresco post Redis flush.

**Causa raíz:** Las clases compiladas en `target/` estaban desactualizadas. El código fuente en `CreateCareerSnapshotUseCaseImpl.java` (commit `47bcb51`) ya contenía la lógica correcta para setear `userSessionTeamId` cuando `isUserTeam = true`. La recompilación con `mvn compile` resolvió el bug sin necesidad de cambios en el código fuente.

**Hipótesis confirmada:** Hipótesis 1 se descarta (la comparación String.equals funcionaba correctamente). El problema era **clases stale en target/**.

## Reproducción (Fase A)

- ✅ Bug reproducido con user fresco (`f30b47bc-6355-4d22-871c-cf5ac65c38fc`):
  - `userTeamId: null`
  - `userSessionTeamId: null`
  - `squadSize: 0`
- ✅ Confirmado: el backend estaba usando clases compiladas anteriores a BUG-002

## Diagnóstico (Fase B)

Se agregaron logs temporales de debug en:
- `StartCareerUseCaseImpl.start()` — DEBUG-001 y DEBUG-004
- `CreateCareerSnapshotUseCaseImpl.create()` — DEBUG-002 y DEBUG-003

**Logs clave (app.log):**
```
[CREATE-CAREER-DEBUG-003] worldTeamId=8e55b18e-051d-48bd-9763-a35ae3005ac0, isUserTeam=true
[CAREER-START-DEBUG-004] after save: userSessionTeamId=7bf18a64-f4e2-42fe-886c-32dbcdcd254e, squadSize=132
```

El equipo Real Madrid (`8e55b18e-051d-48bd-9763-a35ae3005ac0`) se detecta correctamente como `isUserTeam=true` y los valores se setean correctamente. **El código era correcto; la recompilación lo resolvió.**

## Fix aplicado (Fase C)

**No se requieren cambios en código fuente.** El código ya era correcto en `47bcb51`.

Acción tomada: `mvn compile` para recompilar las clases. El proceso Spring Boot debe reiniciarse post-compilación para cargar las nuevas clases.

**Logs temporales de debug fueron agregados y luego removidos** (para diagnóstico, no como fix).

## Test E2E (Fase D)

Archivo: `src/test/java/com/footballmanager/adapters/in/web/career/e2e/CareerSquadPopulationE2ETest.java`

**Test agregado:**
- `careerCreation_withFreshUser_hasValidUserSessionTeamId()` — verifica que después de crear career, `userSessionTeamId != null`, `userTeamId != null`, `squadSize > 0` y `totalRounds > 0`

**Tests existentes ya cubrían:**
- `careerCreation_populatesSquadWithPlayers()` — squad >= 11 jugadores
- `careerCreation_autoSelectLineup_returns200()` — auto-select sin NPE
- `noCareer_getMyTeam_returns200not500()` — manejo sin career

## Validación (Fase E)

| Suite | Tests | Resultado |
|---|---|---|
| Focused | 13 | ✅ PASS |
| Broad | 333 | ✅ PASS |
| Full | 932 | ✅ PASS |

**Smoke manual post-fix:**
- UserId: `dc4fbc25-6bfd-4a1a-b7ff-5b6b62082302`
- `userTeamId: 0e95f2fb-5247-4344-b41f-d83eabc30a8f` ✅ (no null)
- `userSessionTeamId: 0e95f2fb-5247-4344-b41f-d83eabc30a8f` ✅ (no null)
- `squadSize: 11` ✅
- `totalRounds: 10` ✅ (no 0)

## Archivos modificados

| Path | Cambio | Concern |
|---|---|---|
| `src/test/java/.../CareerSquadPopulationE2ETest.java` | Test nuevo agregado | E2E coverage para BUG-003 |

**Archivos fuente sin cambios:** `StartCareerUseCaseImpl.java`, `CreateCareerSnapshotUseCaseImpl.java` — código ya era correcto.

## Confirmaciones

- [✅] Bug reproducido con user fresco post Redis flush
- [✅] Causa raíz identificada: clases compiladas desactualizadas
- [✅] Test E2E agregado para `userSessionTeamId != null`
- [✅] Tests focused: 13 PASS
- [✅] Tests broad: 333 PASS
- [✅] Tests full: 932 PASS
- [✅] Smoke manual: userSessionTeamId != null, squadSize = 11, totalRounds = 10
- [✅] Reporte `.md` escrito
- [✅] NO push realizado

## Pendiente

- [ ] Push (requiere autorización del usuario)
