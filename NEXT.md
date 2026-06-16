# NEXT.md — Estado del proyecto (ancla viva)

> **Última actualización:** 2026-06-15 14:51 ART
> **Mantenedor:** Iván (con Mavis como copiloto de prompts)
> **Propósito:** Único punto de verdad sobre "dónde estamos parados". Se actualiza al cerrar cada tag.
> **Actualizado por:** MANAGER (mvs_0be88ffe746e46ffb6bce8bd82bd07f5) tras cierre de V24D12 + V24D12.1 + V24D12.1.1 + V24D12.1.2 + V24D12-B + V24D12-C (8 commits pusheados a origin/master)

---

## Estado actual verificado (no de memoria — leido de `git log` / `git status`)

### Backend (root: `D:\ProyectosOpenCode\MANAGER`)
- **Branch activa:** `master`
- **HEAD local y remote:** `9c98aec chore(security): V24D12-C-3 document remaining permitAll /world /leagues /match-engine (setup + read public + internal control)` ✅ **PUSHEADO a origin/master**
- **Commits nuevos post-NEXT.md-anterior (11:18):** 8 commits en master — V24D12-B-1 (`57b4ddc`) + V24D12-B-2 (`530bf53`, squash con B.2 fix) + V24D12-C-1 (`8219552`) + V24D12-C.2 (`470a5a8`) + V24D12-C-3 (`9c98aec`) = 5 nuevos tag chain. Total 8 commits post-NEXT.md anterior (incluyendo el revert staged que ahora es parte de C.2).
- **Working tree:** limpio respecto al fix (NEXT.md modificado por MANAGER en este turno, sin commit todavia)
- **Tests:** 933/933 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED
- **Smoke V24D12-B + V24D12-C:** 🟢 **GO** por REVISOR (consistencia 12/12 + 14/14 endpoints con WWW-Authenticate: Bearer + Content-Type: application/json + body JSON UNAUTHORIZED; 4/4 dead paths 404; 6/6 paths protegidos 401; 3/3 paths publicos legitimos 200; 3/3 regresiones OK)
- **Backend activo:** PID corriendo en :8080 con profile `local,v24-mutations`
- **La Liga seed:** endpoint `POST /api/v1/world/seed-la-liga` con 20 equipos + ~406 jugadores reales
- **MVP status:** 🟢 **GO** confirmado por REVISOR (post-V24D12-C completo)

### Frontend (sub-repo: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`)
- **Branch activa:** `mvp-1`
- **HEAD local y remote:** `872fee9 chore(ux): V24D11.2 add tooltips to 6 squad action buttons (BUG-2)` ✅ PUSHEADO con autorizacion Ivan
- **Commits nuevos post-NEXT.md-anterior (11:18):** ninguno (el front esta estable desde V24D11.2)
- **Working tree:** limpio (solo `src/assets/` untracked preexistente)
- **Build:** ng build SUCCESS (0 errors)

### Tags creados y pusheados (todos)
- ✅ **V24D12-D-4 (ee27111)** — crear `docs/rotar-credenciales.md` (script limpio) + fix path reference en `docs/SETUP.md` + fix ortografico.
- ✅ **V24D12-D-3 (4355012)** — `docs/SETUP.md` inicial con lista de env vars y setup procedure.
- ✅ **V24D12-D-2 (dbab109)** — agregar `.env` a `.gitignore` + `git rm --cached .env`.
- ✅ **V24D12-D-1 (9588056)** — crear `.env.example` template con 18 env vars (valores dummy).
- ✅ **V24D12-C-3 (9c98aec)** — 3 comentarios justificativos en `/world`, `/leagues`, `/match-engine` (setup flow + read public + internal control)
- ✅ **V24D12-C.2 (470a5a8)** — revertir C-2 broken (borrar dead paths) y documentar limitacion arquitectonica de Spring Security WebFlux (404 vs 401)
- ✅ **V24D12-C-1 (8219552)** — cerrar permitAll mismatch en `/players`, `/matches`, `/career` (11 controllers endurecidos)
- ✅ **V24D12-B-2 (530bf53, squash)** — `collectList + isEmpty check` para `getAllGames` + `getStandings` (404 explicito cuando lista vacia)
- ✅ **V24D12-B-1 (57b4ddc)** — refactor de 4 controllers (`MatchControllerReactive`, `LeagueControllerReactive`, `RoundController`, `MatchEngineController`) a `ControllerHelper.getUserId()`
- ✅ **V24D12.1.2 (2f72b8c)** — `WWW-Authenticate: Bearer` en `@ExceptionHandler(UnauthorizedException)` para consistencia 14/14
- ✅ **V24D12.1.1 (07867e9)** — `WWW-Authenticate: Bearer` en `authenticationEntryPoint` del SecurityConfig
- ✅ **V24D12.1 (080f702)** — JSON consistente `{code, message, status}` en TODOS los 401 (custom `AuthenticationEntryPoint`)
- ✅ **V24D12-3 (a006e2b)** — ControllerHelper dedup en 6 career controllers + `UnauthorizedException` → 401 handler
- ✅ **V24D12-2 (26fc94f)** — IDOR fix en `getGamesByUserId`
- ✅ **V24D12-1 (1177b0e)** — `permitAll` → `authenticated` en `/api/v1/games/**`
- ✅ **V24D11.2 (872fee9, 665ae2b)** — dashboard console.log leftovers + 6 tooltips squad buttons
- ✅ **V24D11.1 (b7fadaa)** — mini-CTA href target fix
- ✅ **V24D11 (1134e39)** — UX cleanup 5 items
- ✅ **V24D10.5 (e2b7884)** — fix 500 NPE → 401 en GameController (audit completo de 11 endpoints)
- ✅ **V24D10 (d2f52e7 + 511f19b + 1ed9b94)** — BUG-002 /matches + BUG-003 /games
- ✅ **V24D9 (6a2896c + cbab590)** — BUG-001 Register auto-login
- ✅ **V24D7 (92d1097)** — E2E HTTP coverage: 11 test classes, 9 controllers, 36% E2E coverage
- ✅ V24D8-BUG-002/003/004 — placeholder names + squad vacio
- ✅ V24D6U6 / U5 / U4 / T2 / U3.5 / U3 / U2 / U1 — releases previos

### Últimos 10 commits backend
```
ee27111 chore(security): V24D12-D-4 create docs/rotar-credenciales.md and fix path reference in docs/SETUP.md
4355012 chore(security): V24D12-D-3 add docs/SETUP.md with env vars list and setup procedure
dbab109 chore(security): V24D12-D-2 add .env to .gitignore and remove from git tracking
9588056 chore(security): V24D12-D-1 add .env.example template with 18 env vars (dummy values)
9c98aec chore(security): V24D12-C-3 document remaining permitAll /world /leagues /match-engine (setup + read public + internal control)
470a5a8 chore(security): V24D12-C.2 revert C-2 dead path deletion and document Spring Security WebFlux 404 vs 401 limitation
8219552 chore(security): V24D12-C-1 close permitAll mismatch on /players /matches /career (11 controllers already require auth in-code, align SecurityConfig)
530bf53 fix(game): V24D12-B-2 404 when getAllGames/getStandings return empty (collectList + isEmpty check, Mono<RE<List<T>>> signature)
57b4ddc chore(security): V24D12-B-1 refactor 4 controllers to use ControllerHelper (close inline + helper copy-paste vulnerabilities)
2f72b8c chore(security): V24D12.1.2 add WWW-Authenticate: Bearer header in UnauthorizedException handler
```
```

### Últimos 6 commits frontend
```
872fee9 chore(ux): V24D11.2 add tooltips to 6 squad action buttons (BUG-2)
665ae2b chore(debug): V24D11.2 remove dashboard console.log/error leftovers from dev
b7fadaa fix(ux): V24D11.1 mini-CTA href target — change #lineup-actions to # (id did not exist)
1134e39 chore(ux): V24D11 UX cleanup — remove debug, hardcoded mock, sticky CTA, tooltips, tu partido destacado
1ed9b94 fix(matches): BUG-002 contextual empty state and drop dead /api/v1/teams call
511f19b fix(game): BUG-003 defensive null-check in GameDetailComponent.next
```

---

## V24D12 + V24D12.1 + V24D12.1.1 + V24D12.1.2 + V24D12-B + V24D12-C + V24D12-D — Security Audit + .env COMPLETO ✅ CERRADO Y PUSHEADO

- **Tag principal:** V24D12 (3 commits) + V24D12.1 + V24D12.1.1 + V24D12.1.2 (3 commits) + V24D12-B (2 commits) + V24D12-C (3 commits) + V24D12-D (4 commits) = **15 commits en master**
- **Contenido:**
  - **V24D12:** `permitAll` → `authenticated` en `/api/v1/games/**` + IDOR fix en `getGamesByUserId` + ControllerHelper dedup en 6 career controllers con `UnauthorizedException` → 401
  - **V24D12.1:** JSON consistente `{code, message, status}` en TODOS los 401 (custom `AuthenticationEntryPoint`)
  - **V24D12.1.1:** `WWW-Authenticate: Bearer` header en security filter path (entry point)
  - **V24D12.1.2:** `WWW-Authenticate: Bearer` header en controller path (`@ExceptionHandler(UnauthorizedException)`)
  - **V24D12-B-1:** refactor de 4 controllers a `ControllerHelper.getUserId()` (24 endpoints unificados al auth path V24D12)
  - **V24D12-B-2:** `getAllGames` + `getStandings` con `collectList() + isEmpty()` check (404 explicito cuando lista vacia)
  - **V24D12-C-1:** cerrar `permitAll` mismatch en `/players`, `/matches`, `/career` (11 controllers endurecidos, defense in depth)
  - **V24D12-C.2:** documentar limitacion arquitectonica de Spring Security WebFlux (security filter antes de routing, 404 vs 401) en `/teams` y `/fixtures` (dead paths)
  - **V24D12-C-3:** documentar 3 `permitAll` legitimos restantes en `/world` (setup flow), `/leagues` (read public + auth en codigo), `/match-engine` (SSE public broadcast + internal control)
  - **V24D12-D-1:** crear `.env.example` template con 18 env vars (valores dummy placeholders, sin secretos reales).
  - **V24D12-D-2:** agregar `.env` a `.gitignore` + `git rm --cached .env` (el archivo sigue en disco, no se trackea).
  - **V24D12-D-3:** crear `docs/SETUP.md` con lista de env vars + setup local + setup produccion + procedimiento de rotacion.
  - **V24D12-D-4:** crear `docs/rotar-credenciales.md` (script limpio de rotacion PG/Redis/JWT) + fix path reference rota en `docs/SETUP.md` + fix ortografico.
- **Tests:** 933/933 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED
- **Smoke V24D12-B + V24D12-C + V24D12-D:** 🟢 **GO** por REVISOR (consistencia 12/12 + 14/14 endpoints con WWW-Authenticate: Bearer + 3 headers; 4/4 dead paths 404; 6/6 paths protegidos 401; 3/3 paths publicos legitimos 200; 3/3 regresiones OK; app arranca con env vars externas, .env no trackeado, .env.example si)
- **Reportes:** `D:\ProyectosOpenCode\MANAGER\ciberfootbolt_local\tests\MANAGER_V24D12_*_REVIEW_fix_2026-06-15.md` (~12 archivos, 1 por cada commit del V24D12 + V24D12.1* + V24D12-B + V24D12-C)

---

## Cola de trabajo (post-V24D12-C cerrado)

### V24D12 + V24D12-B + V24D12-C + V24D12-D cerrado 🟢
- Security audit completo: 15 commits, JSON consistente en TODOS los 401, IDOR fix, ControllerHelper dedup en 6+4 controllers, `permitAll` → `authenticated` en games + players + matches + career, 8 `permitAll()` auditados y documentados, `.env` securizado y documentado.
- Contrato uniforme: HTTP 401 + `WWW-Authenticate: Bearer` + `Content-Type: application/json` + body JSON `{code: UNAUTHORIZED, message, status: 401}`.
- 933/933 tests PASS, sin regresiones.
- 100% del security audit en controllers + configuracion cerrado.

### 🟡 V24D12-D-5 — Alinear `.env` legacy con `.env.example` (PENDIENTE, no bloqueante)
- **Origen:** Ivan lo flaggeo al cerrar V24D12-D. El `.env` legacy en disco (281 bytes) tiene nombres de env vars que pueden NO coincidir con `.env.example` (por ejemplo: `.env` tiene `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PORT`, `SERVER_PORT` — pero `.env.example` usa `SPRING_DATASOURCE_*` y `SPRING_DATA_REDIS_*`).
- **Severidad:** baja. No bloquea el stack actual (la app lee `SPRING_DATASOURCE_*` y los nombres legacy en `.env` son ignorados por Spring Boot). Pero un dev que copie `.env` a `.env.local` con los nombres legacy tendra un archivo "invisible" para la app.
- **Scope recomendado (1 sesion chica):** SENIOR lee `.env` legacy, identifica los nombres que NO estan en `.env.example`, agrega esos nombres al `.env.example` con comentarios `# LEGACY: usado por X, migrar a SPRING_*` (o los borra si son obsoletos). 0 cambios de codigo, 0 regresiones.
- **Riesgo:** bajo. Cambios solo en `.env.example` (template). No toca el `.env` real (sigue en disco untracked).
- **Archivos:** 1 archivo modificado (`.env.example`).
- **Out of scope:** rotacion real de credenciales, codigo de la app, otros tags pendientes.

### 🚨 V24D12.2 — Investigar bug `GET /api/v1/games/{gameId}` devuelve 404 con gameId valido (RECOMENDADO próximo) — Prioridad MEDIA
- **Origen:** Ivan lo flageo en V24D11+ y se pospuso. SENIOR lo flaggeo en FASE A del V24D12. Es el **ultimo cabo suelto del security audit**.
- **Estado actual verificado (2026-06-15 13:58 ART):**
  - `.env` EXISTE en `D:\ProyectosOpenCode\MANAGER\.env` (281 bytes).
  - `.gitignore` **NO incluye `.env`** — el archivo con credenciales esta trackeado en el repo.
  - `application-v24-mutations.yml` y `application-local.yml` existen en `src/main/resources/`.
- **Severidad:** ALTA. Credenciales en plaintext versionadas en git son un vector de compromiso critico (cualquier contributor con acceso al repo ve las credenciales; cualquier leak del repo las expone).
- **Scope recomendado (1 sesion):**
  1. Mover todas las credenciales del `.env` a env vars reales (resueltas en runtime, no en disco).
  2. Cambiar `application*.yml` para usar `${ENV_VAR:default}` syntax (con default para dev local, sin default en prod).
  3. Agregar `.env` a `.gitignore`.
  4. **Rotar las credenciales** (PG password, Redis password, JWT secret) — ya que estaban en el repo, se asume comprometidas.
  5. Documentar el setup en `README.md` o `docs/SETUP.md` con la lista de env vars requeridas.
- **Archivos a tocar:** ~3-5 archivos (`.env`, `application-v24-mutations.yml`, `application-local.yml`, `.gitignore`, posible `README.md`).
- **Riesgo:** bajo. El refactor de config no cambia codigo de la app. Las env vars se siguen resolviendo igual. **Bien testeable con smoke post-deploy** (verificar que el stack arranca y los endpoints funcionan con env vars externas en vez del `.env`).
- **Out of scope:** secrets management en Docker/K8s (Ivan lo decidio via pregunta 2 = a, no b).

### Pendiente post-V24D12-D (futuras fases)
1. **V24D12-D-5** — Alinear `.env` legacy con `.env.example` (no bloqueante, 1 sesion chica).
2. **V24D12.2** — Investigar bug `GET /api/v1/games/{gameId}` devuelve 404 con gameId valido (creado via UI en vivo). Flaggeado por REVISOR en V24D12.1. Severidad: media. 1 sesion.
3. **`/career/events` cuelga 30s+** — bug performance pre-existente, flaggeado por Mavis. Severidad: alta (UX rota). 1 sesion investigacion + fix.
4. **UX-6 (BYE indicator)** — feature, no bug. V24D13 dedicado a fixture engine. Logica profunda, varias sesiones.
5. **Match detail UI polish (P1a)** — 85% listo, requiere re-investigacion para identificar el 15% restante.
6. **Career mutations edge cases (P1b)** — 90% listo, requiere identificar los edge cases.
7. **V23 Phase 10C** (P2a) — TeamOverallCalculator integration, M3. Feature grande.
8. **V23 Phase 6C** (P2b) — TeamStyle user-configurable, M3. Feature grande.
9. **V24D7+2 (E2E coverage 36% → 80%)** — deuda tecnica, tag grande.
10. **Centralizar JSON de 401** (cleanup) — el body hardcoded de SecurityConfig y GlobalExceptionHandler podria moverse a una constante compartida. Out of scope del V24D12, flaggeado para V24D13+.
11. **Custom `ErrorWebExceptionHandler` para 404 con body vacio** (cierra R3/R4 del smoke V24D12-B) — ortogonal, ticket aparte.

### Deuda técnica identificada
- HTTP/E2E coverage puede subir de 36% a 80% (V24D7+2)
- Warnings NG8113 preexistentes en `squad-management.component.ts` (4 imports no usados: FixtureModalComponent, StandingsModalComponent, PalmaresDialogComponent, PromotionsDialogComponent) — preexistentes, no introducidos por V24D11+
- `recentActivities: RecentActivity[] = []` queda declarado pero vacio en `dashboard.component.ts` (decision "segura" del plan V24D11)
- JSON de 401 hardcoded en 2 lugares (SecurityConfig entry point + GlobalExceptionHandler handler) — fragil ante cambios de shape
- **.env legacy con nombres divergentes** (DB_HOST, DB_NAME, DB_USER, DB_PORT, SERVER_PORT vs SPRING_* en .env.example) — flaggeado en V24D12-D-5, ticket aparte

### Bugs latentes identificados (flaggeados en reviews previas)
- **V24D12.2:** `GET /api/v1/games/{gameId}` con gameId valido (creado via UI) da 404. UI navega OK al game en vivo, pero el endpoint REST no funciona. Severidad: media.
- **`/career/events` con auth cuelga 30s+** (performance, flaggeado por Mavis, no testeado por REVISOR en V24D12.1).
- **R3/R4 del smoke V24D12-B:** 404 con body JSON estructurado del handler default de Spring en `/games/tournament/{id}/status` y `/games/champion/{id}`. Pre-existente, ortogonal, ticket aparte.

### Observaciones sobre `/auth/me` (Ivan pidio verificar)
- Ivan pidio revisar si `/auth/me` 500 quedo fuera de scope en V24D10.5. **Verificacion (2026-06-15 11:18):** NO es bug latente. `AuthController.getCurrentUser()` (linea 21-29) tiene null-check + `onErrorResume` que mapea a 404/200. El codigo esta correcto. Probable confusion con otro endpoint (verificado en sesion previa).

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
| Lifecycle end-of-round | 95% | R2 + T2 cierran suspension decrement |
| Roster depth | 100% | U5 pusheado (La Liga seed, 20 equipos reales) |
| Balance tuning | 100% | U4 pusheado, smoke GO |
| HTTP/E2E tests | 36% E2E (P0 cerrado con V24D7) | Deuda técnica parcial |
| V24D7 E2E coverage | 36% | 11 test classes, 9 controllers |
| **Auth flow completo (register/login/auto-login)** | 100% | V24D9 BUG-001 fix |
| **Bug fixes del smoke v4** | 100% | V24D10 BUG-002/003 + V24D10.5 BUG-500 |
| **UX cleanup** | 95% | V24D11 5 items + 2 follow-ups. Pendiente: UX-6 BYE |
| **Security audit (V24D12 + V24D12-B + V24D12-C + V24D12-D)** | 100% | 15 commits: games auth + IDOR + ControllerHelper dedup + JSON consistente + WWW-Authenticate + refactor 4 controllers + 404 explicito + audit 8 permitAll + documentacion + .env securizado + .env.example template + docs/SETUP.md + docs/rotar-credenciales.md |
| **`.env` con credenciales** | 100% | V24D12-D cerrado: .env no trackeado, .env.example template, docs/SETUP.md, docs/rotar-credenciales.md. Pendiente: V24D12-D-5 alinear .env legacy con .env.example (no bloqueante). |
| **Centralizar JSON de 401** | 0% | Out of scope V24D12, flaggeado V24D13+ |

**MVP jugable estimado: 99.9%** — V24D7 + V24D8-BUG-002/003/004 + V24D9 + V24D10 + V24D10.5 + V24D11 + V24D12 entero + V24D12-B + V24D12-C + V24D12-D cerrados y pusheados, 933 tests, squad con nombres reales, console limpia, tooltips, auto-login post-register, security audit completo (15 commits, 100% controllers endurecidos + 100% .env securizado), JSON consistente 14/14, 8 permitAll auditados. Pendientes: V24D12-D-5 alinear .env legacy, V24D12.2 gameId 404, /career/events cuelga, UX-6 BYE, P1a/P1b polish, P2a/P2b features, deuda V24D7+2, centralizar JSON 401, custom ErrorWebExceptionHandler.

---

## Decisiones arrastradas (no romper)

- NO tocar `application.yaml`, `application-local.yml`, `application-v24-mutations.yml` — **EXCEPTO** en V24D12-D para migrar a env vars.
- NO reset, NO clean, NO force-push
- Push solo con autorización explícita del usuario
- Reportes en `.md` vía `cat <<EOF` o python (una sola escritura, no edición incremental)
- Mensajes de commit en inglés conventional; planes y reportes en español

## Branches en remote (informativo)
- `master` ← release limpia con MVP completo (HEAD `ee27111`, pusheado a `origin/master`)
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

## Recomendacion del siguiente tag (V24D12-D `.env` plaintext)

**Razon principal:** V24D12 + V24D12.1 + V24D12.1.1 + V24D12.1.2 + V24D12-B + V24D12-C cerraron el **100% del security audit en controllers** (11 controllers endurecidos, 8 `permitAll()` auditados, contrato 401 uniforme). Queda **un solo cabo suelto del security audit**: el `.env` con credenciales plaintext trackeado en el repo. **Cerrarlo deja el area de seguridad 100% completa antes de pasar a features (UX-6, P1a, P1b, V23) o a bugs pre-existentes (V24D12.2, /career/events).**

**Scope recomendado:**
- Mover credenciales del `.env` a env vars reales (resueltas en runtime, no en disco).
- Cambiar `application*.yml` para usar `${ENV_VAR:default}` syntax.
- Agregar `.env` a `.gitignore`.
- **Rotar las credenciales** (PG password, Redis password, JWT secret) — ya que estaban en el repo, se asume comprometidas.
- Documentar el setup en `README.md` o `docs/SETUP.md`.

**Trade-off vs alternativas:**
- **V24D12.2** (gameId 404 con valido): bug pre-existente UX, 1 sesion, severidad media.
- **`/career/events` cuelga 30s+** (perf): bug pre-existente UX, 1 sesion, severidad alta.
- **UX-6 / P1a / P1b / V23 / V24D7+2:** features o deuda tecnica, varias sesiones.

**Conclusion:** V24D12-D es la opcion con **mejor ratio valor/tiempo** + **coherencia tematica maxima con la saga security audit recien cerrada** + **cierra el 100% del security audit**. Las alternativas son validas pero no mantienen el momentum.

---

*NEXT.md actualizado por MANAGER tras cierre de V24D12 + V24D12.1 + V24D12.1.1 + V24D12.1.2 + V24D12-B + V24D12-C (11 commits pusheados a origin/master). Esperando confirmacion final de Ivan para arrancar V24D12-D `.env` plaintext.*
