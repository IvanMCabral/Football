# MANAGER Project — Status, Stack, Bugs, Workflow

> **Última actualización**: 2026-07-10 17:22 ART
> **Versión del sprint activo**: V25D99.20.3.1 (en `feat/v25d99.20.3.1-runtime-fixes`, NO mergeada a main)
> **Owner**: Iván Cabral · **Stack**: Angular 21 + Spring Boot 3.2.1 + WebFlux + R2DBC (PostgreSQL 15) + Redis + Java 21

---

## 1. ¿Dónde estamos?

### 1.1 ¿Qué es MANAGER?

**ciber-footbolt** (code name) — manager de fútbol *turn-based* con simulación de partidos, chemistry engine y squad editor con drag-drop. Stack:

- **Frontend**: Angular 21 + Material 21 + CDK drag-drop + RxJS, corriendo en `http://localhost:4200`.
- **Backend**: Spring Boot 3.2.1 con WebFlux (reactivo, no servlet), R2DBC para PostgreSQL, Redis para career saves y cache LRU, corriendo en `http://localhost:8080`.
- **DB**: PostgreSQL 15.6 con Flyway migrations (schema version 12 actualmente).
- **Cache**: Redis en `127.0.0.1:6379` con auth (password en `application-local.properties`).
- **JDK**: Java 21.0.8 (`C:\Users\ichu_\.jdk\jdk-21.0.8`).

### 1.2 Sprints recientes (qué arreglamos, qué falta)

| Sprint | Scope | Status | Notas |
|---|---|---|---|
| V25D99.19 | BUG-1: ChemistryBreakdown slot-category fallback | ✅ merged | Mejora cálculo chemistry. |
| V25D99.20.0 C2 | BUG-4 drag snap-back + BUG-5 mojibake textual | ✅ merged | Sprint cross-cutting combinó 2 fixes. |
| V25D99.20.1 | `/career/lineup/current` usa `CareerSessionService` cache LRU | ✅ merged | Pre-fix: race entre Redis read y cache. |
| **V25D99.20.2** | BUG-4 fix: persiste `customXPercent`/`customYPercent` en storage | ✅ local, NO merged | Raw Object storage + typed getter/setter. 3 pinning tests pass. |
| **V25D99.20.3** | Cross-cutting: modal onFormationChange + BUG-2 slot cleanup + BUG-3 charset UTF-8 | ✅ local, NO merged | Fix crítico Iván (chem=83 post-cycle → 91) RESUELTO. |
| **V25D99.20.3.1** | Follow-up: gap BUG-2 runtime + modal close handler + mojibake modal | ✅ local, NO merged | Tests verde, smoke bloqueado por Redis reset. |

**Total**: 6 sprints en V25D99.* — la mayoría bugs visuales + 1 bug crítico de storage (V25D99.20.2). El sprint activo (V25D99.20.3.1) tiene código que **NO está en `main`** — sigue en feat branch.

### 1.3 Branch actual

```
$ git branch --show-current
feat/v25d99.20.3.1-runtime-fixes
$ git log --oneline -3
dd4b129 fix(lineup+match-engine) V25D99.20.3.1-BACK: BUG-1 JSON round-trip gap + BUG-3 charset on match-engine controllers
0a196a8 (frontend) BUG-2 gap fix
8e58663 test(lineup) V25D99.20.3-BACK: pinning tests for BUG-2 stale slots + BUG-3 UTF-8 charset
```

⚠️ **Esta branch NO se ha pusheado al remote.** Tags locales `release-V25D99.20.3.1-BACK` + `release-V25D99.20.3.1-FRONT` aplicados, esperando GO de Iván para push.

---

## 2. ¿Está bien como lo estamos haciendo?

### 2.1 Lo que funciona bien ✅

- **Tests JUnit verdes**: mvn 2345/15fails (15 pre-existing en `LineupCommandUseCaseImpl*Test` que datan de V25D99.20.1). Frontend ng 513/1fail pre-existing.
- **Pinning tests atrapan regresiones específicas**: cada sprint agrega 2-3 tests de pinning que documentan el bug + verifican el fix.
- **Sprint cross-cutting (C2) redujo N pushes a 1**: V25D99.20.0 + V25D99.20.3 combinaron fixes funcionales + visuales + infra en un solo atomic push.
- **Multi-agent workflow**: Mavis (root, no toca código) → SENIOR (code+push) → Iván (decisión). Cada rol tiene scope claro.
- **Self-test UI descubrió bugs reales** (chem=83 post-cycle) que el pinning test JUnit no capturó — bug real de runtime vs test gap.
- **Regla "cerrar modal antes de validar state final"** capturada en memoria tras V25D99.20.2 self-test.

### 2.2 Lo que NO está bien ❌

- **Gap entre pinning test JUnit y runtime real**: V25D99.20.3 BUG-2 (clear stale slots) tiene pinning test que PASA pero en runtime real el backend persiste 14 slots. La auto-select no limpia keys stale del map legacy en runtime. **Falta un integration test** que use `CareerSaveRedisRepository.save()` + reload + assert slots.
- **Modal "Editor de Formación" no cierra con Escape ni backdrop click**: bug UI pre-existente, no estaba en scope V25D99.20.3. Quedó para V25D99.20.3.1.
- **Mojibake (letras raras) persiste en modal a pesar de charset=UTF-8**: `âœ•`, `â€"/99`, `Ã—95%`. El charset=UTF-8 funciona en API pero el frontend renderiza mal el modal. Probable: sub-endpoint o campo string-encoded que no pasa por el charset filter.
- **Stack down events frecuentes**: Redis y backend crashean en momentos sin explicación clara. El script `restart-redis.ps1` existe para recuperación pero requiere ejecución manual.
- **Push gate estricto bloquea progreso**: cada sprint espera OK Iván para push y merge. Si Iván está ausente >24h, los sprints se quedan en local.
- **3 crons duplicados** (`check-v25d99.20`, `check-v25d99.20.1`, etc.) — generatean ticks redundantes cada hora. Cleanup pendiente.

### 2.3 Decisión recomendada sobre cómo seguir

- **Cerrar el sprint V25D99.20.3.1 antes de empezar uno nuevo** — código estable + smoke pass + push + merge a main.
- **Agregar integration tests** a partir de ahora: el gap JUnit-vs-runtime es un riesgo serio.
- **Limpiar crons duplicados** en una pasada de hygiene.
- **Documentar el stack down recovery** (Redis restart + backend restart) — el script `restart-redis.ps1` ya existe pero no hay equivalente para backend.

---

## 3. Bugs por pantalla (estado actual)

### 3.1 Frontend

| Pantalla | Bug | Status | Sprint |
|---|---|---|---|
| Login (`/login`) | OK | — | — |
| Dashboard (`/dashboard`) | OK | — | — |
| Squad Management (`/squad`) | "14/11 jugadores" header muestra slots stale | Pendiente | V25D99.20.3.1 (BUG-2 gap) |
| Squad page chem/coverage | OK después de V25D99.20.3 (no baja a 83) | ✅ | — |
| Modal "Editor de Formación" | "Proyectando chemistry..." carga colgada | Pendiente | V25D99.20.3.1 (BUG modal) |
| Modal "Editor de Formación" | No cierra con Escape ni backdrop click | Pendiente | V25D99.20.3.1 |
| Modal "Editor de Formación" | Moijibake: `âœ•`, `â€"/99`, `Ã—95%` | Pendiente | V25D99.20.3.1 (BUG encoding) |
| Squad editor drag-drop | OK desde V25D99.20.0 (snap-back) + V25D99.20.2 (customX/Y persistence) | ✅ | — |
| Match dialogs (formation-modal) | OK | — | — |

### 3.2 Backend

| Endpoint | Bug | Status | Sprint |
|---|---|---|---|
| `GET /career/lineup/current` | Retorna 0B si cache miss post-restart (pre-existente de V25D99.20.1) | Pendiente | No priorizado |
| `GET /career/lineup/current` | Charset=UTF-8 OK | ✅ | V25D99.20.3 |
| `POST /career/lineup/auto-select` | Charset=UTF-8 OK | ✅ | V25D99.20.3 |
| `POST /career/lineup/auto-select` | NO limpia slots stale (BUG-2 gap) | Pendiente | V25D99.20.3.1 |
| `POST /career/lineup/preview-chemistry` | Probable mojibake sub-endpoint | Pendiente | V25D99.20.3.1 |
| `GET /api/v1/auth/login` | Charset sin UTF-8 (no priorizado) | Pendiente | — |
| `POST /api/v1/career/start` | OK | — | — |
| `GET /api/v1/career/status` | OK | — | — |
| `GET /api/v1/world/leagues` | OK | — | — |
| `POST /api/v1/world/seed-la-liga` | OK | — | — |

### 3.3 Schema / Persistence

- **Flyway warning** pre-existente: "Schema 'public' has version 12, but no migration could be resolved in the configured locations!" — log de cada startup, no bloquea. Ocurre porque hay 4 SQL migrations con nombre no estándar que Flyway ignora.
- **Career save in Redis** con `Map<String, Map<String, Object>>` raw storage para soportar dual format (legacy String + new LineupSlotDTO).

---

## 4. Cómo se levanta el server

### 4.1 Prerequisitos

- Java 21 (`C:\Users\ichu_\.jdk\jdk-21.0.8`).
- Node.js + npm (Angular 21).
- PostgreSQL 15.6 corriendo con DB `football_manager` + user `postgres` + password rotado (ver `application-local.properties`).
- Redis corriendo en `127.0.0.1:6379` con auth password (ver `application-local.properties`).

### 4.2 Levantar el stack completo desde cero

```powershell
# 1. Redis (si no está corriendo)
powershell -NoProfile -ExecutionPolicy Bypass -File "D:\ProyectosOpenCode\MANAGER\restart-redis.ps1"

# 2. Backend Spring Boot
$env:DB_PASSWORD = "Mgr2026Rot!Secure#"
$env:DB_USER = "postgres"
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "football_manager"
$env:JWT_SECRET = "exJDmBLu+dhN5md5EkZW1r5qRKF/R8ECjc+Rp4ynVfdi9NA7nwAf5z2635ZtI4CWekeXMwpQXouI8QaUHOaTOw=="
$env:REDIS_PASSWORD = "MgrRedis2026!Rotate#Secure"
cd D:\ProyectosOpenCode\MANAGER
mvn package -DskipTests
Start-Process mvn.cmd -ArgumentList "spring-boot:run","-Dspring-boot.run.profiles=local,v24-mutations" `
  -RedirectStandardOutput "D:\ProyectosOpenCode\MANAGER\logs\backend.log" `
  -RedirectStandardError "D:\ProyectosOpenCode\MANAGER\logs\backend.err" `
  -WindowStyle Hidden

# 3. Frontend Angular
cd D:\ProyectosOpenCode\MANAGER\front-ciber\project
Start-Process npm.cmd -ArgumentList "start" `
  -RedirectStandardOutput "D:\ProyectosOpenCode\MANAGER\logs\frontend.log" `
  -RedirectStandardError "D:\ProyectosOpenCode\MANAGER\logs\frontend.err" `
  -WindowStyle Hidden
```

### 4.3 Verificar que está UP

```powershell
Get-NetTCPConnection -LocalPort 8080,4200,6379,5432 -State Listen
# Esperado: 8080 (backend), 4200 (frontend), 6379 (redis), 5432 (postgres) todos UP.
```

```powershell
$body = @{ email="smoke_20260708214053@test.com"; password="Pw2026!" } | ConvertTo-Json
$l = Invoke-WebRequest -Uri "http://[::1]:8080/api/v1/auth/login" -Method POST -Body $body -ContentType "application/json"
$token = ($l.Content | ConvertFrom-Json).accessToken
$s = Invoke-WebRequest -Uri "http://[::1]:8080/api/v1/career/status" -Headers @{Authorization="Bearer $token"}
# Esperado: HTTP 200, JSON con careerPhase.
```

### 4.4 Recrear career del smoke user (si Redis se reseteó)

```powershell
$body = @{ email="smoke_20260708214053@test.com"; password="Pw2026!" } | ConvertTo-Json
$l = Invoke-WebRequest -Uri "http://[::1]:8080/api/v1/auth/login" -Method POST -Body $body -ContentType "application/json"
$token = ($l.Content | ConvertFrom-Json).accessToken
$uid = "0b51b19b-8ea0-48ea-9a30-591e312ce1ee"
$h = @{Authorization="Bearer $token"; "Content-Type"="application/json"}

# 1. Seed LaLiga (60 teams, 1006 players)
Invoke-WebRequest -Uri "http://[::1]:8080/api/v1/world/seed-la-liga?userId=$uid" -Method POST -Headers $h

# 2. Start career con Real Madrid
Invoke-WebRequest -Uri "http://[::1]:8080/api/v1/career/start" -Method POST -Headers $h `
  -Body '{"leagueId":"4feeb9df-4133-4655-883e-e96894907e7b","teamId":"339bd1ba-94df-33c5-b533-19dea5cc1757","difficulty":"NORMAL","gameSpeed":"NORMAL","teamsPerDivision":5}' `
  -ContentType "application/json"

# 3. Auto-select 4-4-2
Invoke-WebRequest -Uri "http://[::1]:8080/api/v1/career/lineup/auto-select" -Method POST -Headers $h `
  -Body '{"formation":"4-4-2"}' -ContentType "application/json"
```

### 4.5 Bajar el stack

```powershell
# Backend
Get-Process -Name "java" | Stop-Process -Force

# Frontend
Get-Process -Name "node" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*ng serve*" } | Stop-Process -Force

# Redis
powershell -NoProfile -ExecutionPolicy Bypass -File "D:\ProyectosOpenCode\MANAGER\restart-redis.ps1" -Status
# Si UP, kill manual:
# Get-Process -Name "redis-server" | Stop-Process -Force

# Postgres no se baja (es Windows service).
```

---

## 5. Multi-agent workflow (cómo trabaja Mavis)

### 5.1 Roles

- **Mavis (root, mavis agent)**: orquesta. NO toca código ni push. Sintetiza status para Iván. Kickea SENIOR, recibe reports.
- **SENIOR (senior-football agent)**: codea + pushea + corre tests. Recibe briefs, escribe reportes `.md`, reporta SMOKE PASS/FAIL.
- **REVISOR (verifier agent)**: NO restart infra. Solo review/test/verify sobre deliverable existente.
- **MANAGER**: genera prompts de sprint cross-cutting. Mavis muestra "¿aprobamos?" → Iván sí → recién kick a SENIOR.

### 5.2 Sprint flow típico

1. Iván reporta un bug o pide feature.
2. Mavis investiga (Read/Grep/curl), propone scope.
3. Mavis kicka SENIOR con brief `.md` (en workspace, ref <500 chars en el prompt).
4. SENIOR codea + corre tests + pushea branch + reporta.
5. Mavis verifica disco (Disco > contexto), kickea REVISOR si necesita audit.
6. Mavis hace self-test UI cuando es sprint con cambios visibles.
7. Iván decide A/B/C (merge / fix forward / rollback).
8. SENIOR merge a main solo con OK Iván.

### 5.3 Reglas críticas

- **Push gate**: requires OK explícito Iván. Tag/push branch OK, merge a main NO.
- **Disco > contexto**: Grep/Read/curl antes de declarar "está cerrado". Contexto miente.
- **ACK loop prevention**: 1ª ACK de cierre, 2ª STOP ACKs explícito. No más respuestas a esa sesión.
- **Self-test UI completo**: incluir cerrar modal antes de validar state final.
- **REVISOR ≠ restart**: si el stack no responde, REVISOR avisa a Mavis root, no reinicia.

---

## 6. Tokens + credenciales (sensibles, NO commitear)

⚠️ **NO copiar a git, NO compartir fuera del equipo.** El `.properties` y `.yml` tienen estos secretos:

```properties
# application-local.properties
spring.datasource.password=Mgr2026Rot!Secure#
spring.datasource.hikari.password=Mgr2026Rot!Secure#
spring.data.redis.password=MgrRedis2026!Rotate#Secure
jwt.secret=exJDmBLu+dhN5md5EkZW1r5qRKF/R8ECjc+Rp4ynVfdi9NA7nwAf5z2635ZtI4CWekeXMwpQXouI8QaUHOaTOw==
```

```yaml
# application-local.yml (mismas keys)
spring:
  datasource:
    password: "Mgr2026Rot!Secure#"
    hikari:
      password: "Mgr2026Rot!Secure#"
  data:
    redis:
      password: "MgrRedis2026!Rotate#Secure"
jwt:
  secret: "exJDmBLu+dhN5md5EkZW1r5qRKF/R8ECjc+Rp4ynVfdi9NA7nwAf5z2635ZtI4CWekeXMwpQXouI8QaUHOaTOw=="
```

**Smoke user** (testing):
- email: `smoke_20260708214053@test.com`
- password: `Pw2026!`
- user_id: `0b51b19b-8ea0-48ea-9a30-591e312ce1ee`
- league: La Liga 2024/25 (`4feeb9df-4133-4655-883e-e96894907e7b`)
- team: Real Madrid (`339bd1ba-94df-33c5-b533-19dea5cc1757`)

---

## 7. Cosas pendientes (todo list)

### Inmediato (V25D99.20.3.1 cierre)

- [ ] Iván GO push `feat/v25d99.20.3.1-runtime-fixes` al remote.
- [ ] SENIOR push (con tag `release-V25D99.20.3.1-BACK` + `release-V25D99.20.3.1-FRONT`).
- [ ] Self-test UI completo (4-4-2 → 4-3-3 → 4-4-2 → close modal → screenshot).
- [ ] Verificar slots=11 después del cycle (no 14).
- [ ] Verificar modal cierra con Escape.
- [ ] Verificar mojibake eliminado.
- [ ] Merge `feat/v25d99.20.3.1-runtime-fixes` a `main` (con OK Iván).

### Sprint siguiente (V25D99.21+)

- [ ] Integration test con `CareerSaveRedisRepository.save()` + reload + assert slots count (cerrar gap JUnit-vs-runtime).
- [ ] Sub-endpoint mojibake investigation (probable: `/lineup/preview-chemistry`).
- [ ] Modal close handler: agregar `(keydown.escape)` y verificar `disableClose` config.
- [ ] Charset UTF-8 en TODOS los controllers (no solo `/lineup/current` + `/lineup/auto-select`).
- [ ] `/career/lineup/current` cache miss fallback: agregar fallback a `careerRepository.findById` cuando `getCareerFromCache` retorna empty.

### Hygiene

- [ ] Limpiar 3 crons duplicados (`check-v25d99.20` + `check-v25d99.20.1`).
- [ ] Documentar stack down recovery (`backend-down` equivalente de `restart-redis.ps1`).
- [ ] Stale session cleanup (memory regla: sesiones >24h finished).

---

## 8. Contacto + referencias

- **Working language**: Castellano rioplatense (vos, che, dale).
- **Owner**: Iván Cabral (`ichu_@example.com` en commits).
- **Mavis root session**: `mvs_3f18031aaa7b4cd6a4e35a40d2a83f30`.
- **SENIOR session**: `mvs_d3067909f4f84f339f114e25ec98c454`.
- **Repos**: `D:\ProyectosOpenCode\MANAGER` (backend + submodule frontend).
- **Logs**: `D:\ProyectosOpenCode\MANAGER\logs\backend.log`, `frontend.log`, `redis.log` (en `C:\temp\redis\`).
- **Mavis memory**: `C:\Users\ichu_\.mavis\agents\mavis\memory\MEMORY.md` + topic files en `manager-project.md`, `sprint-C2-cross-cutting-V25D99.20.md`.
- **Smoke script**: `C:\temp\repro_v4.py` (SENIOR lo corre para validar fixes).

---

## 9. TL;DR (5 segundos)

- **Stack**: Angular 21 + Spring Boot 3.2.1 + Postgres 15 + Redis, todo en `localhost`.
- **Sprint activo**: V25D99.20.3.1 (código local, NO mergeado). 3 fixes: gap BUG-2 + modal close + mojibake.
- **Bugs pendientes principales**: (1) integration test gap, (2) modal close handler, (3) mojibake frontend.
- **Levantar**: `restart-redis.ps1` + `mvn spring-boot:run` con env vars + `npm start`.
- **Bajar**: `Stop-Process` java + node + redis-server.
- **OK Iván necesario para**: push al remote, merge a main, decisión de scope V25D99.21.
