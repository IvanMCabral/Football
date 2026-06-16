# NEXT.md — Estado del proyecto (ancla viva)

> **Última actualización:** 2026-06-16 06:27 ART
> **Mantenedor:** Iván (con Mavis como copiloto de prompts)
> **Propósito:** Único punto de verdad sobre "dónde estamos parados". Se actualiza al cerrar cada tag.
> **Actualizado por:** Mavis root tras cierre de UX-6 BYE Indicator (backend + frontend)

---

## Estado actual verificado (no de memoria — leído de `git log` / `git status`)

### Backend (root: `D:\ProyectosOpenCode\MANAGER`)
- **Branch activa:** `master`
- **HEAD local y remote:** `66a0a32 feat: UX-6 BYE indicator — backend DTOs, services and endpoints` ✅ **PUSHEADO a origin/master**
- **Commits nuevos post-anterior NEXT.md (6 commits):**
  - `66a0a32` UX-6 backend — BYE indicator DTOs, services, endpoints
  - `aad2b8f` UX-6 frontend — BYE indicator en round-live, round-summary, dashboard-fixture-modal
  - `e6529cc` — Update NEXT.md con V24D12.2 + V24D13-1 + V24D12-D-6
  - `d442403` V24D12.2 — GameEntity Jackson deserialization fix
  - `b3e25cb` V24D13 — Update NEXT.md con V24D12-D-6 + V24D13-1
  - `819ec49` V24D13-1 — SSE heartbeat 15s en CareerNotificationService
- **Working tree:** limpio
- **Tests:** 933/933 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED
- **Smoke V24D12.2:** 🟢 **GO** por Mavis root — `GET /games/{id}` → 200 post-fix

### Frontend (sub-repo: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`)
- **Branch activa:** `mvp-1`
- **HEAD local y remote:** `aad2b8f feat(ux): UX-6 BYE indicator — frontend components` ✅ PUSHEADO a origin/mvp-1
- **Working tree:** limpio
- **Build:** ng build SUCCESS (0 errors)

### Tags creados y pusheados (todos)
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
66a0a32 feat: UX-6 BYE indicator — backend DTOs, services and endpoints
aad2b8f feat: UX-6 BYE indicator — frontend components
e6529cc chore: update NEXT.md — V24D12.2 + V24D13-1 + V24D12-D-6 closed
d442403 fix: add @NoArgsConstructor and @Setter to GameEntity for Jackson deserialization
b3e25cb chore: V24D13 update NEXT.md with V24D12-D-6 + V24D13-1 status
6e9ab8e chore(security): V24D12-D-6 add application-local.yml with rotated credentials (gitignored)
dffe02a fix(security): V24D12-D-6 make RedisConfig read password from YAML
819ec49 fix(perf): V24D13-1 add SSE heartbeat every 15s in CareerNotificationService
ee27111 chore(security): V24D12-D-4 create docs/rotar-credenciales.md and fix path reference
4355012 chore(security): V24D12-D-3 add docs/SETUP.md with env vars list and setup procedure
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

### Cola priorizada

| # | Tag | Descripción | Severidad |
|---|---|---|---|
| 1 | P1a | Match detail UI polish (~15% restante) | 🟡 media |
| 3 | P1b | Career mutations edge cases (~10% restante) | 🟡 media |
| 4 | R3/R4 | Custom `ErrorWebExceptionHandler` para 404 con body vacío en `/games/tournament/{id}/status` y `/games/champion/{id}` | 🟢 baja |
| 5 | NG8113 | Warnings imports no usados en `squad-management.component.ts` | 🟢 baja |
| 6 | V24D7+2 | E2E coverage 36% → 80% | 🟡 media |
| 7 | V23 Phase 10C | TeamOverallCalculator integration | 🔴 alta (feature) |
| 8 | V23 Phase 6C | TeamStyle user-configurable | 🔴 alta (feature) |
| 9 | JSON 401 | Centralizar body hardcoded en SecurityConfig + GlobalExceptionHandler | 🟢 baja |

### Deuda técnica identificada
- HTTP/E2E coverage puede subir de 36% a 80% (V24D7+2)
- Warnings NG8113 en `squad-management.component.ts` (4 imports no usados: FixtureModalComponent, StandingsModalComponent, PalmaresDialogComponent, PromotionsDialogComponent) — preexistentes
- `recentActivities: RecentActivity[] = []` declarado pero vacío en `dashboard.component.ts` (decisión "segura" V24D11)
- JSON de 401 hardcoded en 2 lugares — frágil ante cambios de shape
- R3/R4: 404 con body JSON del handler default de Spring en `/games/tournament/{id}/status` y `/games/champion/{id}`

---

## Estimación de progreso MVP

| Área | % | Comentario |
|---|---|---|
| V24 engine | 100% | U4 pusheado, λ empírico en target |
| Career mutations | 90% | Completo MVP, falta edge cases |
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
| **MVP jugable estimado** | **99.9%** | Todos los bugs P0/P1 cerrados. Pendientes: UX-6, P1a/P1b, NG8113, R3/R4, JSON 401, V24D7+2, V23 features |

---

## Decisiones arrastradas (no romper)

- NO reset, NO clean, NO force-push
- Push solo con autorización explícita de Iván
- Reportes en `.md` vía `cat <<EOF` o python (una sola escritura, no edición incremental)
- Mensajes de commit en inglés conventional; planes y reportes en español

## Branches en remote (informativo)
- `master` ← release limpia con MVP completo (HEAD `d442403`, pusheado a `origin/master`)
- `mvp-1` ← branch de trabajo front (HEAD `872fee9`, pusheado a `origin/mvp-1`)
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

*NEXT.md actualizado por Mavis root tras cierre de V24D12.2 + V24D13-1 + V24D12-D-6 + V24D13 (4 commits, todos pusheados a origin/master). Security audit 100% cerrado. Próximo paso: UX-6 BYE indicator.*
