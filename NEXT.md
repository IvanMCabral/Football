# NEXT.md — Estado del proyecto (ancla viva)

> **Última actualización:** 2026-06-18 20:15 ART (post-F6 Sprint 2 merge + pre-Sprint CLEANUP)
> **Mantenedor:** Iván (con Mavis como copiloto de prompts)
> **Propósito:** Único punto de verdad sobre "dónde estamos parados". Se actualiza al cerrar cada tag.
> **Actualizado por:** MANAGER-football en Fase 0 del sprint CLEANUP (housekeeping del ancla, no tocó código).

---

## Estado actual verificado (no de memoria — leído de `git log` / `git status`)

### Backend (root: `D:\ProyectosOpenCode\MANAGER`)
- **Branch activa:** `master` (lista para sprint CLEANUP)
- **HEAD local y remote:** `b1952f5 feat(v24): F6 Match Compare endpoint -- GET /careers/{id}/matches/{id}/compare` ✅ PUSHEADO a origin/master
- **Últimos 8 commits post-anterior NEXT.md:**
  - `b1952f5` — F6 Match Compare endpoint
  - `15b035b` — F6 Match Compare endpoint [hooks reales]
  - `0ff7991` — F6 Match Compare service (DTOs + replay + diff)
  - `363e981` — F6 Match Compare foundation (BaselineState storage TTL 7d)
  - `1ff1225` — F6 F2 contract fix (player-quality modifier to chanceProbability)
  - `60f9de2` — F5.3.4 test E2E pause/resume round
  - `f8ca76e` — F5.3.2 BUG-015 back (pause/resume round endpoints)
  - `6aa0292` — F5.2 BUG-009 + BUG-010 (filter noise events + derive possession)
- **Working tree:** `MANAGER_TEAM_RUNBOOK.md` modificado + archivos untracked (planes V23, scripts, browser helpers, tests viejos en `ciberfootbolt_local/tests/`). NO contaminar con el sprint CLEANUP.
- **Tests:** **1350 tests / 6 FAIL / 0 errors / 0 skipped** (con `DB_PASSWORD` + `REDIS_PASSWORD` exportadas, ver runbook §11.1)
- **6 FAIL conocidos (objetivo del sprint CLEANUP):**
  - 3 `RoundControllerE2ETest` (controllers vs contratos desincronizados post-V24D13-2)
  - 1 `CachingRandomWrapperTest.invalidateFromIndex_preservesPrefixDiscardsSuffix` (BUG-007 F5.1 fragility)
  - 2 `V24LiveSessionTest.recordManualSubstitution_altersResult*` (NUEVOS post V24D6U4-RE recalibración)
- **Smoke REVISOR F6 Sprint 2:** 🔴 **NO-GO** (BACK `/compare` 404 a pesar de detail OK). Causa raíz identificada: `BaselineStateRedisAdapter.save()` swallowea excepciones.

### Frontend (sub-repo: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`)
- **Branch activa:** `mvp-1`
- **HEAD local y remote:** `0e8aaa6 test(match-detail): F6 Match Compare -- service + page specs` ✅ PUSHEADO a origin/mvp-1
- **Últimos 7 commits:**
  - `0e8aaa6` — F6 Match Compare tests
  - `80cee80` — F6 Match Compare source
  - `c7e704a` — F5.3.3 fix (pause BEFORE dialog.open race-condition)
  - `339004e` — F5.3.4 specs (BUG-015 front)
  - `0d6e95b` — F5.3.3 feat (pause/resume round when modal opens)
  - `465c281` — F5.3.1 revert (BUG-012 remove auto-advance)
  - `c6f582b` — F5.2 BUG-012 (auto-advance, luego revertido)
- **Working tree:** limpio (sin cambios tracked).
- **Build:** ng build SUCCESS.
- **Tests:** 4 FAIL pre-existentes (objetivo sprint CLEANUP):
  - 3 `MatchComparePageComponent` (Zone.js ProxyZone no se establece)
  - 1 `SeasonStatsTabComponent` (scope contract scope=team + teamId=non-empty)

### Tags creados y pusheados (todos)

- ✅ **V24D14-LIVE-F6-MATCH-COMPARE** — 4 commits del F6 Sprint 2 (363e981, 0ff7991, 15b035b, b1952f5). **Backend merged a master pero smoke REVISOR NO-GO** — fix pendiente en sprint CLEANUP.
- ✅ **V24D14-LIVE-F6-F2-CONTRACT-FIX** — `1ff1225` player-quality modifier to chanceProbability.
- ✅ **V24D14-LIVE-F5.3-LECTURA-A** — F5.3 Lectura A fixes (BUG-012 revert + BUG-015 pause/resume).
- ✅ **V24D14-LIVE-F5.2** — F5.2 fixes (BUG-009/010/011/012).
- ✅ **V24D14-LIVE-F5.1** — F5.1 fixes (BUG-004/007/008).
- ✅ **V24D14-LIVE-F3.0** — F3 POC frontend.
- ✅ **V24D7-V24D6U4-RE** — Recalibración V24 model a Poisson λ=1.25 (commit `864b4f2`). **Causó 2 tests V24LiveSession FAIL nuevos.**
- ✅ **V24D14** — Housekeeping (NEXT.md sync, runbook §8 cleanup, gitignore `src/assets/`).
- ✅ **V24D14-LIVE-F2** + **V24D14-LIVE-F2.5** — F2 F2.5 contract + deferred substitutions.
- ✅ **V24D13-2** — FIX-001 (refactor `startMatches` 100% reactivo, respetar 4xx protocol semantics).
- ✅ **V24D13-1** — SSE heartbeat 15s en CareerNotificationService (`/career/events` no cuelga 30s+).
- ✅ **V24D12.2** — `@NoArgsConstructor` + `@Setter` en `GameEntity` (deserialization Jackson).
- ✅ **V24D12-D-6** — RedisConfig + application-local.yml con credenciales rotadas.
- ✅ **V24D12-B/C/D** — ControllerHelper dedup, permitAll → authenticated, .env securizado, docs.
- ✅ **V24D12.1 / 12.1.1 / 12.1.2** — JSON consistente en TODOS los 401 + WWW-Authenticate: Bearer.
- ✅ **V24D11.2 / V24D11.1 / V24D11** — UX cleanup (5 items + dashboard console + mini-CTA).
- ✅ **V24D10.5 / V24D10** — BUG-002/003 fixes + NPE → 401 en GameController.
- ✅ **V24D9** — BUG-001 Register auto-login.
- ✅ **V24D8-BUG-002/003/004** — placeholder names + squad vacío + seed real player names.
- ✅ **V24D7** — E2E HTTP coverage 36% (11 test classes, 9 controllers).
- ✅ **V24D6U6/U5/U4/T2/U3.5/U3/U2/U1** — releases previos.
- ✅ **UX-6** — BYE indicator en fixture (backend + frontend).
- ✅ **P1b** — Career mutations edge cases (58 tests nuevos, 0 production changes, mergeado fast-forward a master).

---

## Seguridad — 100% CERRADA ✅

**V24D12 + V24D12.1 + V24D12.1.1 + V24D12.1.2 + V24D12-B + V24D12-C + V24D12-D + V24D12-D-6 + V24D12.2 + UX-6 + F5.x + F6.x = ~30 commits en master**

- Contrato uniforme: HTTP 401 + `WWW-Authenticate: Bearer` + `Content-Type: application/json` + body `{code, message, status}` en TODOS los endpoints protegidos.
- IDOR fix en `getGamesByUserId`.
- ControllerHelper dedup en 6+4 controllers.
- `permitAll` → `authenticated` en games + players + matches + career.
- 8 `permitAll()` auditados y documentados.
- `.env` no trackeado + credenciales rotadas en `application-local.yml` (gitignored).
- `RedisConfig` con password leído del YAML.
- F6 Sprint 2: nuevo path `/api/v1/careers/{id}/matches/{id}/compare` para baseline-vs-live diff (BUG en baseline persistencia — sprint CLEANUP).

---

## Cola priorizada — Sprint CLEANUP (post-F6 Sprint 2)

### 🔴 CRÍTICOS (bloquean features)

| # | Tag | Bug | Effort | Riesgo |
|---|---|---|---|---|
| 1 | V24D15-CLEANUP-1 | **BUG_COMPARE_404** (F6 Sprint 2) — `/compare` 404 a pesar de `/detail` 200. Causa: `BaselineStateRedisAdapter.save()` swallowea excepciones | 1-2 días (β reactivo) | medio |
| 2 | V24D15-CLEANUP-2 | **BUG_GAME_DASHBOARD_404** (pre-existente) — `/games/{gameId}` 404, partido NO se persiste como Game entity. 3 hipótesis (regresión / caso edge / feature). Investigación runtime primero | 4-8 h | medio-alto |

### 🟡 MENORES (UX / deuda técnica)

| # | Tag | Bug | Effort | Riesgo |
|---|---|---|---|---|
| 3 | V24D15-CLEANUP-3 | **BUG_COMPARE_UX** (F6 Sprint 2) — frontend falta `catchError(() => of(null))` en `MatchCompareApiService.getMatchCompare()` | 5 min | cero |
| 4 | V24D15-CLEANUP-4 | **3 tests FAIL MatchComparePageComponent** — Zone.js ProxyZone no se establece con `waitForAsync/fakeAsync` + `of()` | 2-4 h | bajo |
| 5 | V24D15-CLEANUP-5 | **3 tests FAIL RoundControllerE2ETest** — post-V24D13-2 contratos desincronizados | 1-2 h | bajo |
| 6 | V24D15-CLEANUP-6 | **1 test FAIL CachingRandomWrapperTest.invalidateFromIndex_preservesPrefixDiscardsSuffix** — BUG-007 F5.1 fragility | 2-4 h | bajo |
| 7 | V24D15-CLEANUP-7 | **2 tests FAIL V24LiveSessionTest.recordManualSubstitution_altersResult*** — víctimas recalibración V24D6U4-RE | 1-2 h | bajo |
| 8 | V24D15-CLEANUP-8 | **1 test FAIL SeasonStatsTabComponent** — scope contract | 1-2 h | bajo |
| 9 | V24D15-CLEANUP-9 | **BUG-003 E2E reproducer** (F5.2) — squad fallback funciona pero no hay E2E test que reproduzca 422 | 2-3 h | bajo |
| 10 | V24D15-CLEANUP-10 | **BUG-009 spec threshold** (F5.2) — noise events filter sin threshold concreto en spec | 1 h | cero |
| 11 | V24D15-CLEANUP-11 | **Browser MCP memory leak** (F5.2) — workaround en runbook + `--max-duration 25m` | 15 min | cero |

### 🟢 OBSERVACIONES (limpieza opcional)

| # | Tag | Item | Effort | Decisión |
|---|---|---|---|---|
| 12 | V24D15-CLEANUP-12 | **110 console.log/error/warn en frontend** — limpieza selectiva top 8 archivos | 2-4 h | D5 ✓ |
| 13 | V24D15-CLEANUP-13 | **Velocidad round 90'→3min wall time** — comportamiento pre-existente, DOCUMENTAR (no fix) | 30 min | D4 ✓ |
| 14 | V24D15-CLEANUP-14 | **TODOs en código legacy** (`DefaultMatchCommandApplier`, `MatchFinishService`, `MatchCommandHandler`) — confirmar si V24 los usa; si no → ticket separado | 15 min | out scope si V24 no los usa |

### Effort total estimado

| Fase | Items | Effort |
|---|---|---|
| Fase 0 (housekeeping NEXT.md) | hecho por MANAGER | 15 min ✓ |
| Fase 1 (CRÍTICOS backend) | 2 | 1-2 días |
| Fase 2 (UX + Front tests) | 2 | 3-5 h |
| Fase 3 (Tests FAIL back) | 3 | 4-8 h |
| Fase 4 (Tests FAIL front) | 1 | 1-2 h |
| Fase 5 (Pre-F6 bugs) | 3 | 3-4 h |
| Fase 6 (Console cleanup) | 1 | 2-4 h |
| Fase 7 (Validación mvn+ng test + smoke) | infra | 1-2 h |
| **TOTAL sprint CLEANUP** | **14 items** | **~6-9 días SENIOR** |

---

## Bugs cerrados ✅

- ✅ **V24D12.2:** `GET /games/{id}` 404 → `@NoArgsConstructor` + `@Setter` en `GameEntity`. Smoke GO en su momento.
- ✅ **V24D13-1:** `/career/events` cuelgue 30s+ → SSE heartbeat 15s en `CareerNotificationService`.
- ✅ **V24D12-D-6:** `.env` con credenciales → `application-local.yml` con credenciales rotadas (gitignored) + `RedisConfig` con `@Value`.
- ✅ **F5.x BUG-004/007/008/009/010/011/012/015:** corregidos en F5.1-F5.3 y mergeados a master.
- ✅ **F6 Sprint 1 contract fix (1ff1225):** player-quality modifier agregado a `chanceProbability`.
- ✅ **F6 Sprint 2 código mergeado** a master (4 commits + 1 contract) — **smoke NO-GO pendiente**, fix en sprint CLEANUP.
- ✅ **V24D6U4-RE recalibración** (864b4f2): λ de 0.45 a 1.22 (target 1.25). Tests ajustados pendientes.
- ✅ **UX-6:** BYE indicator en fixture.
- ✅ **P1b:** Career mutations edge cases (58 tests nuevos).

---

## ❌ OBSOLETO (de F4)

- ❌ **OBSOLETO — V24D12-D-5** — Alinear `.env` legacy con `.env.example` (reemplazado por V24D12-D-6).
- ❌ **OBSOLETO — R3/R4** — Custom ErrorWebExceptionHandler para 404 (V24D12-B.2 cerró).
- ❌ **OBSOLETO — NG8113** — Warnings squad-management.component.ts (commit `f3dd8f8` cerró).
- ❌ **OBSOLETO — V23 Phase 10C** — TeamOverallCalculator integración V23 path (mergeado desde 10C1-10C4).
- ❌ **REMOVIDO — V23 Phase 6C** — TeamStyle user-configurable (nunca implementado, no aparece en planes V23).
- ❌ **OBSOLETO — P1b** — Career mutations edge cases (cerrado por `5a92227`, +58 tests).

---

## Estimación de progreso MVP

| Área | % | Comentario |
|---|---|---|
| V24 engine | 100% | U4 + recalibración V24D6U4-RE pusheados |
| Career mutations | 100% | P1b cerrado (commit `5a92227`) |
| Stats | 90% | Completo MVP |
| Lineups backend | 95% | U1+U2 cierran blockers |
| Lineups frontend | 99% | U3+U3.5+T2+V24D11+F5.3 pusheados |
| Match detail UI | 85% | Disparos, ratings, timeline OK |
| Lifecycle end-of-round | 95% | R2+T2 cierran suspension decrement |
| Roster depth | 100% | U5 pusheado (La Liga seed, 20 equipos reales) |
| Balance tuning | 100% | U4 + V24D6U4-RE pusheados, λ en target |
| LIVE-MATCH (pause/resume/subs/formation) | 95% | F5.3 GO; F6 compare pendiente fix |
| HTTP/E2E tests | 36% E2E | 11 test classes, 9 controllers |
| Auth flow | 100% | V24D9 BUG-001 fix + V24D12 audit |
| Bug fixes smoke v4 | 100% | V24D10 BUG-002/003 + V24D10.5 BUG-500 |
| UX cleanup | 95% | V24D11 + UX-6 + console cleanup pendiente |
| Security audit | 100% | ~30 commits: games auth + IDOR + ControllerHelper + JSON consistente + WWW-Authenticate + audit 8 permitAll + .env securizado |
| GameEntity deserialization | 100% | V24D12.2 |
| /career/events performance | 100% | V24D13-1 |
| /compare endpoint (compare baseline-vs-live) | **50%** | código mergeado a master, smoke NO-GO (BUG_COMPARE_404) |
| **MVP jugable estimado** | **99.9%** | Solo falta CLEANUP sprint para subir a 99.95% |

---

## Decisiones arrastradas (no romper)

- NO reset, NO clean, NO force-push
- Push solo con autorización explícita de Iván
- Reportes en `.md` vía `cat <<EOF` o python (una sola escritura, no edición incremental)
- Mensajes de commit en inglés conventional; planes y reportes en español
- Sprint CLEANUP: NO inflar scope (D1=β, D2=investigar runtime, D3=ajustar tests, D4=documentar, D5=console cleanup, D6=NEXT.md housekeeping hecho)

## Branches en remote (informativo)

- `master` ← release limpia con MVP completo (HEAD `b1952f5`, pusheado a `origin/master`)
- `mvp-1` ← branch de trabajo front (HEAD `0e8aaa6`, pusheado a `origin/mvp-1`)
- `feature/v23-poisson-goal-model` (paralelo V23)
- `feature/v33-tactical-chance-quality` (paralelo)
- `mvp-1-performance-cleanup` → mergeado en master
- `test/p1b-career-mutations-edges`, `test/v24d7plus2-*` (branches de tests paralelos)
- `chore/v24d14-housekeeping`, `chore/json-401-centralize`, `chore/ng8113-obsolete-housekeeping`, `chore/r3r4-obsolete-housekeeping`, `chore/v23-legacy-cleanup` → mergeados a master

---

## Regla de mantenimiento

Al cerrar cada tag (después del push), actualizar este archivo:
1. Mover el tag recién cerrado a la sección "Tags creados y pusheados"
2. Actualizar "Estado actual verificado"
3. Re-escribir con `cat <<EOF` o python, no edición incremental

Si en una sesión nueva Mavis no lee este archivo, pedirselo explícitamente:
> "Lee `NEXT.md` y decime dónde estamos"

---

## Observaciones documentadas (V24D15-CLEANUP — Fase 8, 2026-06-18 20:50 ART)

### Velocidad round (pre-existente, NO fix)
- **Comportamiento actual:** un partido de 90' tarda ~3 minutos wall-time en correr end-to-end (medido en smoke F3/F5). Esto NO es un bug — es el resultado del wall-clock-per-minute configurado en `simulation.use-v24-detailed-engine` (default = 2s por minuto simulado).
- **Pre-existente:** documentado desde V24D6U4-RE. Aplica a TODOS los matches (no solo V24), tanto en live como en replay.
- **Smoke scope para REVISOR:** NO validar timing estricto. El smoke acepta que un partido de 90' demore entre 2.5 y 4 minutos wall-time. Si excede 5 min → flag para investigación (posible degradación de performance).
- **Workaround (si Iván quiere más rápido):** cambiar `simulation.wall-clock-per-minute=1000` en `application-local.yml` → 1 segundo por minuto simulado (partido completo en ~90 segundos). NO aplicado por defecto para mantener el "feel" del juego.

### TODOs legacy (DefaultMatchCommandApplier, MatchFinishService, MatchCommandHandler)
- **Confirmado por grep call-sites (Fase 8):** ninguno de los 3 archivos con TODOs legacy es usado por el path V24 (que va por `SubstitutionCommandUseCaseImpl` + `V24LiveSession` + `RoundController`).
  - `MatchFinishService` — 0 call sites en `src/main/java` (huérfano).
  - `DefaultMatchCommandApplier` — 0 call sites en `src/main/java` (huérfano).
  - `MatchCommandHandler` — usado por `ExecuteMatchCommandUseCaseImpl`, `MatchTickService`, `MatchSession` (legacy pre-V24, NO V24).
- **Decisión:** out of scope para sprint CLEANUP. Ticket separado si Iván quiere limpieza (recomendado — el código es V23 legacy que ya no se ejecuta en runtime).
- **TODOs documentados:**
  - `DefaultMatchCommandApplier.java:55` — "Implementar lógica de sustitución cuando se definan las reglas de negocio"
  - `DefaultMatchCommandApplier.java:59` — "Implementar lógica de mentalidad cuando se definan las reglas de negocio"
  - `MatchFinishService.java:65` — "Obtener seasonKey desde Match o Game"
  - `MatchFinishService.java:99` — "Implementar cuando se defina la estructura de mentalidad"
  - `MatchCommandHandler.java:99,164` — "Implementar" (sin contexto)

---

*NEXT.md actualizado por MANAGER-football en Fase 0 del sprint CLEANUP (2026-06-18 20:15 ART). Próxima actualización al cerrar V24D15-CLEANUP.*
