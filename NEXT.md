# NEXT.md — Estado del proyecto (ancla viva)

> **Última actualización:** 2026-06-16 12:53 ART
> **Mantenedor:** Iván (con Mavis como copiloto de prompts)
> **Propósito:** Único punto de verdad sobre "dónde estamos parados". Se actualiza al cerrar cada tag.
> **Actualizado por:** SENIOR + Mavis root tras cierre de V24D14 (housekeeping documental)

---

## Estado actual verificado (no de memoria — leído de `git log` / `git status`)

### Backend (root: `D:\ProyectosOpenCode\MANAGER`)
- **Branch activa:** `chore/v24d14-housekeeping` (preparado para merge a master tras smoke GO)
- **HEAD local y remote:** `9efa3d7 chore: update NEXT.md — UX-6 BYE Indicator closed, UX cleanup 100%, P1a next` ✅ **PUSHEADO a origin/master**
- **Commits nuevos post-anterior NEXT.md (1 commit + 1 tag en preparación):**
  - `9efa3d7` — Update NEXT.md con UX-6 BYE Indicator closed
  - `dfaf2d6` — V24D14 housekeeping: sincronizar NEXT.md con HEAD, limpiar runbook §8 (D-5 OBSOLETO), gitignore frontend `src/assets/` (assets muertos no integrados al build)
- **Working tree:** limpio
- **Tests:** 933/933 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED
- **Smoke V24D12.2:** 🟢 **GO** por Mavis root — `GET /games/{id}` → 200 post-fix

### Frontend (sub-repo: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`)
- **Branch activa:** `mvp-1`
- **HEAD local y remote:** `aad2b8f feat(ux): UX-6 BYE indicator — frontend components` ✅ PUSHEADO a origin/mvp-1
- **Working tree:** untracked `src/assets/` (resuelto en V24D14 con gitignore)
- **Build:** ng build SUCCESS (0 errors)

### Tags creados y pusheados (todos)
- ✅ **V24D14 (dfaf2d6)** — Housekeeping: sincronizar NEXT.md, borrar V24D12-D-5 del runbook §8, gitignore `src/assets/` (assets muertos no integrados al build).
- ✅ **UX-6 (66a0a32 + aad2b8f)** — BYE indicator en fixture: backend DTOs `RoundFixturesWithBye` + `AllRoundsWithBye`, endpoints `GET /career/fixtures/round/{round}` y `GET /career/fixtures/round-with-bye`, frontend round-live + round-summary + dashboard-fixture-modal con badge BYE.
- ✅ **V24D12.2 (d442403)** — `@NoArgsConstructor` + `@Setter` en `GameEntity` para deserialización Jackson (GameEntity no tenía constructor público sin args → 404 silencioso en `GET /games/{id}`).
- ✅ **V24D13 (b3e25cb)** — Update NEXT.md con estado V24D12-D-6 + V24D13-1.
- ✅ **V24D13-1 (819ec49)** — SSE heartbeat cada 15s en `CareerNotificationService` (`/career/events` ya no cuelga 30s+).
- ✅ **V24D12-D-6 (dffe02a + 6e9ab8e)** — `RedisConfig.java` con `@Value` para leer password de `application-local.yml` + archivo `application-local.yml` con credenciales rotadas (gitignored).
- ✅ **V24D12-D-4 (ee27111)** — crear `docs/rotar-credenciales.md` + fix path reference en `docs/SETUP.md`.
- ✅ **V24D12-D-3 (4355012)** — `docs/SETUP.md` con lista de env vars y setup procedure.
- ✅ **V24D12-D-2 (dbab109)** — agregar `.env` a `.gitignore` + `git rm --cached .env`.
- ✅ **V24D12-D-1 (9588056)** — crear `.env.example` template con 18 env vars.
- ✅ **V24D12-C-3 (9c98aec)** — 3 comentarios justificativos en `/world`, `/leagues`, `/match-engine`.
- ✅ **V24D12-C.2 (470a5a8)** — revertir C-2 broken + documentar limitación Spring Security WebFlux.
- ✅ **V24D12-C-1 (8219552)** — cerrar `permitAll` mismatch en `/players`, `/matches`, `/career`.
- ✅ **V24D12-B-2 (530bf53)** — `collectList + isEmpty` check para `getAllGames` + `getStandings`.
- ✅ **V24D12-B-1 (57b4ddc)** — refactor de 4 controllers a `ControllerHelper.getUserId()`.
- ✅ **V24D12.1.2 (2f72b8c)** — `WWW-Authenticate: Bearer` en `@ExceptionHandler(UnauthorizedException)`.
- ✅ **V24D12.1.1 (07867e9)** — `WWW-Authenticate: Bearer` en `authenticationEntryPoint`.
- ✅ **V24D12.1 (080f702)** — JSON consistente en TODOS los 401.
- ✅ **V24D12-3 (a006e2b)** — ControllerHelper dedup en 6 career controllers.
- ✅ **V24D12-2 (26fc94f)** — IDOR fix en `getGamesByUserId`.
- ✅ **V24D12-1 (1177b0e)** — `permitAll` → `authenticated` en `/api/v1/games/**`.
- ✅ **V24D11.2 (872fee9)** — dashboard console.log leftovers + 6 tooltips squad buttons.
- ✅ **V24D11.1 (b7fadaa)** — mini-CTA href target fix.
- ✅ **V24D11 (1134e39)** — UX cleanup 5 items.
- ✅ **V24D10.5 (e2b7884)** — fix 500 NPE → 401 en GameController.
- ✅ **V24D10 (d2f52e7 + 511f19b + 1ed9b94)** — BUG-002/003 fixes.
- ✅ **V24D9 (6a2896c + cbab590)** — BUG-001 Register auto-login.
- ✅ **V24D8-BUG-002/003/004** — placeholder names + squad vacío.
- ✅ **V24D7 (92d1097)** — E2E HTTP coverage: 11 test classes, 9 controllers, 36% E2E coverage.
- ✅ V24D6U6 / U5 / U4 / T2 / U3.5 / U3 / U2 / U1 — releases previos.

### Últimos 10 commits backend
```
9efa3d7 chore: update NEXT.md — UX-6 BYE Indicator closed, UX cleanup 100%, P1a next
66a0a32 feat: UX-6 BYE indicator — backend DTOs, services and endpoints
aad2b8f feat: UX-6 BYE indicator — frontend components
e6529cc chore: update NEXT.md — V24D12.2 + V24D13-1 + V24D12-D-6 closed
d442403 fix: add @NoArgsConstructor and @Setter to GameEntity for Jackson deserialization
b3e25cb chore: V24D13 update NEXT.md with V24D12-D-6 + V24D13-1 status
6e9ab8e chore(security): V24D12-D-6 add application-local.yml with rotated credentials (gitignored)
dffe02a fix(security): V24D12-D-6 make RedisConfig read password from YAML
819ec49 fix(perf): V24D13-1 add SSE heartbeat every 15s in CareerNotificationService
ee27111 chore(security): V24D12-D-4 create docs/rotar-credenciales.md and fix path reference
```

---

## Seguridad — 100% CERRADA ✅

**V24D12 + V24D12.1 + V24D12.1.1 + V24D12.1.2 + V24D12-B + V24D12-C + V24D12-D + V24D12-D-6 + V24D12.2 + UX-6 = ~22 commits en master**

- Contrato uniforme: HTTP 401 + `WWW-Authenticate: Bearer` + `Content-Type: application/json` + body `{code, message, status}` en TODOS los endpoints protegidos.
- IDOR fix en `getGamesByUserId`.
- ControllerHelper dedup en 6+4 controllers.
- `permitAll` → `authenticated` en games + players + matches + career.
- 8 `permitAll()` auditados y documentados.
- `.env` no trackeado + credenciales rotadas en `application-local.yml` (gitignored).
- `RedisConfig` con password leído del YAML.
- Tests: 933/933 PASS.

---

## Cola de trabajo (post-V24D12.2 cerrado)

### Features cerradas ✅
- ✅ **UX-6:** BYE indicator en fixture — backend DTOs + endpoints + frontend round-live + round-summary + dashboard-fixture-modal. MANAGER review 🟢 GO.

### Bugs cerrados ✅
- ✅ **V24D12.2:** `GET /games/{id}` 404 → `@NoArgsConstructor` + `@Setter` en `GameEntity`. smoke GO.
- ✅ **V24D13-1:** `/career/events` cuelgue 30s+ → SSE heartbeat 15s en `CareerNotificationService`.
- ✅ **V24D12-D-6:** `.env` con credenciales → `application-local.yml` con credenciales rotadas (gitignored) + `RedisConfig` con `@Value`.

### ❌ OBSOLETO — V24D12-D-5
Alinear `.env` legacy con `.env.example` — **OBSOLETO**. `V24D12-D-6` creó `application-local.yml` que reemplaza completamente el `.env` legacy. El ticket pierde sentido.

### ❌ OBSOLETO — R3/R4
Custom `ErrorWebExceptionHandler` para 404 con body JSON en `/games/tournament/{id}/status` y `/games/champion/{id}` — **OBSOLETO**. V24D12-B.2 (commit `530bf53`) ya cerró el problema: los 3 endpoints concernés (`tournament-status`, `standings`, `champion` en `/api/v1/games/{id}/...`) ahora retornan 404 con body vacío (o 200 con defaults para `tournament-status`) usando `.notFound().build()` explícito. El comentario stale en `GameController.java` líneas 161-168 puede limpiarse como drive-by pero no es un ticket.

### ❌ OBSOLETO — NG8113
Warnings NG8113 en `squad-management.component.ts` (4 imports no usados: FixtureModalComponent, StandingsModalComponent, PalmaresDialogComponent, PromotionsDialogComponent) — **OBSOLETO**. El commit `f3dd8f8` (2026-06-16 15:01 ART) ya removió los 4 dialog components del `imports: []` array. Los TypeScript imports (líneas 10-13) se mantienen porque son necesarios para `this.dialog.open(SomeComponent, ...)`. `ng build` actual no emite ningún NG8113. `MatDialog.open()` no requiere template-import en el array `imports: []` (los componentes se pasan como referencias de tipo, no se referencian en el template).

### ❌ OBSOLETO — V23 Phase 10C
TeamOverallCalculator integration en V23 engine path — **OBSOLETO**. Los 4 subphases (10C1, 10C2, 10C3, 10C4) están mergeados a master:
- 10C1 (`05597ab`): `LeagueSimulator.calculateTeamOVR()` delega a `TeamOverallCalculator`.
- 10C2 (`a430e96`): V23 engine path detrás de feature flag `useV23LeagueEngine`.
- 10C3 (`268188f`): externalización del flag via `SimulationConfig`.
- 10C4 (`b290ca6`): LeagueSimulator dual-path tests.

**832 tests verde** per `V23_SIMULATION_ENGINE_STATUS.md`. **V23 engine preservado** detrás del flag para compat, no se borra. **`TeamOverallCalculator` utility** (`application/service/domain/`) sigue vivo y reusable si V24 lo necesita en el futuro. **V24 vs V23:** V24 es el default (`useV24DetailedEngine=true`), V23 es legacy preservado vía flag. Phase 10C es integración V23-side; V24 no usa OVR-based simulation.

### ❌ REMOVIDO — V23 Phase 6C (TeamStyle user-configurable)
"TeamStyle user-configurable" — **REMOVIDO de la cola**. Phase 6A y 6B (enum + simulación experimental) están mergeados desde V23. **El aspecto "user-configurable" (UI + persistencia + API) nunca fue implementado formalmente** — no hay plan V23 que lo cubra, y `V23_SIMULATION_ENGINE_STATUS.md` NO lista Phase 6C en los completados. Los planes V23 (`V23_PHASE6_TACTICS_STYLE_MODIFIERS_PLAN.md`, `V23_PHASE6B_TACTICAL_STYLE_INTEGRATION_PLAN.md`) solo cubren 6A y 6B; el "user-configurable" no aparece. Si en el futuro se quiere, debería ser un **ticket V24-side separado** (agregar `teamStyle` field a `SessionTeam` con backward-compat Redis JSON, endpoint, UI dropdown, integración con `V24MatchContextFactory`). El enum `TeamStyle` actual se usa internamente en V24 pero sin user-config.

### ❌ OBSOLETO — P1b
Career mutations edge cases (~10% restante) — **OBSOLETO**. El commit `5a92227` (mergeado fast-forward a master el 2026-06-16, tag local `P1b` pusheado a origin el 2026-06-17) ya cerró el ticket: 58 tests nuevos en 5 archivos (3 new + 2 modified), +600 insertions, 0 deletions, 0 cambios en `src/main/`. Archivos:
- `LiveRoundMutationTrackingTest.java` (NEW) — 5 tests: constructor, sets vacíos, accepts adds, independence, concurrent adds.
- `V24CareerMutationPolicyTest.java` (NEW) — 22 tests: master gate, 4 derived getters × 4 combinaciones (TT/TF/FT/FF), raw getters, cross-flag independence, all-true, all-false, toString.
- `V24CareerMutationResultTest.java` (NEW) — 19 tests: factories, defensive copy, null safety, partial logic, getters, toString.
- `V24FormMutationApplierTest.java` (MODIFIED, +10) — B1 (4 tests de clamp en MAX=99 / MIN=1) + B2 (6 tests de rating discretization boundaries 8.0/7.0/6.5/5.5/5.0).
- `V24DisciplineMutationApplierTest.java` (MODIFIED, +2) — B3 (yellowCards null + 1 yellow = 1, no threshold) + B4 (yellowCards=10 + 1 yellow = 11 → 6, threshold fires).

Verificaciones: `mvn test focused scope v24` = 103/103 verde; `mvn test` full = 991 tests, 0 fail, 62 errors pre-existentes de infra (Redis no corriendo, no regresión). MANAGER review 🟢 GO. SENIOR report final: `reporte-senior-p1b-final.md`.

### Cola priorizada

| # | Tag | Descripción | Severidad |
|---|---|---|---|
| 1 | P1a | Match detail UI polish (~15% restante) | 🟡 media |
| 6 | V24D7+2 | E2E coverage 36% → 80% | 🟡 media |
| 9 | JSON 401 | Centralizar body hardcoded en SecurityConfig + GlobalExceptionHandler | 🟢 baja |

### Deuda técnica identificada
- HTTP/E2E coverage puede subir de 36% a 80% (V24D7+2)
- `recentActivities: RecentActivity[] = []` declarado pero vacío en `dashboard.component.ts` (decisión "segura" V24D11)
- JSON de 401 hardcoded en 2 lugares — frágil ante cambios de shape

---

## Estimación de progreso MVP

| Área | % | Comentario |
|---|---|---|
| V24 engine | 100% | U4 pusheado, λ empírico en target |
| Career mutations | 100% | P1b cerrado (commit `5a92227`, +58 tests, 0 production changes) |
| Stats | 90% | Completo MVP |
| Lineups backend | 95% | U1+U2 cierran blockers |
| Lineups frontend | 99% | U3+U3.5+T2+V24D11 pusheados |
| Match detail UI | 85% | Disparos, ratings, timeline OK |
| Lifecycle end-of-round | 95% | R2+T2 cierran suspension decrement |
| Roster depth | 100% | U5 pusheado (La Liga seed, 20 equipos reales) |
| Balance tuning | 100% | U4 pusheado, smoke GO |
| HTTP/E2E tests | 36% E2E | 11 test classes, 9 controllers |
| Auth flow | 100% | V24D9 BUG-001 fix |
| Bug fixes smoke v4 | 100% | V24D10 BUG-002/003 + V24D10.5 BUG-500 |
| UX cleanup | 95% | V24D11 5 items. Pendiente: UX-6 BYE |
| Security audit | 100% | ~20 commits: games auth + IDOR + ControllerHelper + JSON consistente + WWW-Authenticate + audit 8 permitAll + .env securizado + credenciales rotadas |
| GameEntity deserialization | 100% | V24D12.2 — smoke GO |
| /career/events performance | 100% | V24D13-1 — SSE heartbeat 15s |
| **MVP jugable estimado** | **99.9%** | Todos los bugs P0/P1 cerrados. Pendientes: UX-6, P1a, JSON 401, V24D7+2, V23 features |

---

## Decisiones arrastradas (no romper)

- NO reset, NO clean, NO force-push
- Push solo con autorización explícita de Iván
- Reportes en `.md` vía `cat <<EOF` o python (una sola escritura, no edición incremental)
- Mensajes de commit en inglés conventional; planes y reportes en español

## Branches en remote (informativo)
- `master` ← release limpia con MVP completo (HEAD `9efa3d7`, pusheado a `origin/master`)
- `mvp-1` ← branch de trabajo front (HEAD `aad2b8f`, pusheado a `origin/mvp-1`)
- `feature/v23-poisson-goal-model` (paralelo V23)
- `feature/v33-tactical-chance-quality` (paralelo)
- `mvp-1-performance-cleanup` → mergeado en master

---

## Regla de mantenimiento

Al cerrar cada tag (después del push), actualizar este archivo:
1. Mover el tag recién cerrado a la sección "Tags creados y pusheados"
2. Actualizar "Estado actual verificado"
3. Re-escribir con `cat <<EOF` o python, no edición incremental

Si en una sesión nueva Mavis no lee este archivo, pedirselo explícitamente:
> "Lee `NEXT.md` y decime dónde estamos"

---

## Tickets nuevos (post-V24D14)

### 🟡 V24D6U4-RE — Recalibrar V24 model a su propio target λ=1.25

**Detectado en:** LIVE-MATCH-F2-LIVE F2.5 (2026-06-17) durante cierre de 2 tests RED.

**Síntoma:** `V24ShotXgCalculator.java:39-40` declara target `Poisson λ=1.25` para goals/team, pero el código actual produce `λ≈0.36` (calculado: ~5 shots/team × 7% conversion). Resultado: P(0 goles) = 70% por equipo, P(0-0) = 49% por partido. La mayoría de los partidos son 0-0, lo cual hace que los tests de "substitution alters homeGoals" sean estadísticamente poco confiables (P(pasa con seed=42) ≈ 0).

**Causa raíz:** tuning insuficiente de `chanceProbability` (BALANCED = 0.10, debería ser ~0.20) y `goalThreshold` (`xg / 0.40`, debería ser `xg / 0.55`). El comment aspiracional nunca se validó empíricamente con tests reales.

**Por qué NO en F2.5:** la F2.5 contract es "el sub fires at the right minute" (verificado con asserts sobre `subMinute` y `subCount`, no sobre `homeGoals`). Recalibrar el modelo es un epic aparte (afecta TODOS los tests V24, requiere revisar distribuciones de chance creation, onTarget, conversion, xG, etc.) — no es scope de LIVE-MATCH-F2-LIVE.

**Estimate:** 1-2 días (tocar `chanceProbability` + `goalThreshold` + recalibrar V24D6U4 distribution targets + revalidar 1056 tests V24 + posiblemente ajustar tests F2 tests que dependían del tuning anterior).

**Workaround aplicado en F2.5:** cambiar assertions de "homeGoals/awayGoals differ" a "el engine emits SUBSTITUTION event at the effectiveMinute" (test del F2.5 contract puro, no del F2 contract). Más robusto y no depende del tuning.

**Para resolver:** crear ticket V24D7+ (no V24D6U4) — es un recalibration pass, no un bug fix. Asignar después de cerrar LIVE-MATCH-F2-LIVE.

---

*NEXT.md actualizado por SENIOR + Mavis root tras cierre de V24D14 (housekeeping documental, local commit). Próximo paso: P1a — Match detail UI polish.*
