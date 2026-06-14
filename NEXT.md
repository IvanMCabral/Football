# NEXT.md — Estado del proyecto (ancla viva)

> **Última actualización:** 2026-06-13 21:07 ART
> **Mantenedor:** Iván (con Mavis como copiloto de prompts)
> **Propósito:** Único punto de verdad sobre "dónde estamos parados". Se actualiza al cerrar cada tag.

---

## Estado actual verificado (no de memoria — leido de `git log` / `git status`)

### Backend (root: `D:\ProyectosOpenCode\MANAGER`)
- **Branch activa:** `master` (mvp-1-performance-cleanup mergeado)
- **HEAD local y remote:** `0e5a23d` ✅ **PUSHEADO**
- **Working tree:** limpio
- **Tests:** 869/869 PASS, 0 FAIL, 0 ERROR, 1 SKIPPED
- **Backend activo:** PID corriendo en :8080 con profile `local,v24-mutations`
- **Smoke post-U4:** 🟢 GO — λ empírico en target [1.0, 1.5]
- **La Liga seed:** endpoint `POST /api/v1/world/seed-la-liga` con 20 equipos + ~406 jugadores reales
- **MVP status:** 🟢 **GO** confirmado por Revisa

### Frontend (sub-repo: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`)
- **Branch activa:** `mvp-1`
- **HEAD local y remote:** `af3e5d3 feat(V24D6T2-frontend): add short-handed color coding and banner`
- **Working tree:** limpio (solo untracked preexistentes: `squad-editor-modal/`, `src/assets/`)
- **U3 + U3.5 + T2 pusheados** ✅
- **Build:** ng build SUCCESS (tsc errors pre-existentes en spec files no relacionados)

### Tags creados y pusheados (V24D6U6)
- ✅ V24D6U6 (0e5a23d) — restore README.md and ARCHITECTURE.md from master pre-reset + MVP GO confirmado
- ✅ V24D6U5 (d27d76e) — La Liga real seed (20 equipos, ~406 jugadores)
- ✅ V24D6U4 (cd6a282) — engine balance tuning
- ✅ V24D6T2 (bc85e2e, 82f1a5e, 9c70c24 + af3e5d3) — triage 3 bugs
- ✅ V24D6U3.5 (4ff0f4e) — fix /save → /confirm
- ✅ V24D6U3 (3381732, 514131f, df7f524) — frontend short-handed
- ✅ V24D6U2 (a24c533) — short-handed lineups backend
- ✅ V24D6U1 (11ead32) — NotEnoughPlayersException → 422

### Últimos 6 commits backend
```
0e5a23d docs(V24D6U6): restore README.md and ARCHITECTURE.md from master pre-reset
d27d76e feat(V24D6U5): add La Liga 2024/25 real seed service and data
cd6a282 feat(V24D6U4): tune engine balance - reduce goals from ~5 to ~1.25 per team
9c70c24 fix(V24D6T2-backend): decrement suspensionRemainingMatches end-of-round
82f1a5e fix(V24D6T2-backend): add manual-select 10 and injured tests
bc85e2e fix(V24D6T2-backend): map IllegalState/Argument to 422 in lineup manual-select
```

### Últimos 6 commits frontend
```
af3e5d3 feat(V24D6T2-frontend): add short-handed color coding and banner
4ff0f4e fix(V24D6U3.5-frontend): use /career/lineup/confirm instead of non-existent /save
df7f524 feat(V24D6U3-frontend): enable short-handed confirm in squad editor modal
514131f feat(V24D6U3-frontend): consume lineup warnings in squad management
3381732 feat(V24D6U3-frontend): add LineupWarningDTO model
ccbb273 feat: polish player season stats team scope
```

---

## V24D6U6 — Cierre MVP ✅ CERRADO Y PUSHEADO

- **Tag:** V24D6U6 (0e5a23d)
- **Contenido:** restore README.md y ARCHITECTURE.md from master pre-reset
- **Decisión GO/NO-GO:** 🟢 **MVP GO** confirmado por Revisa
- **Smoke:** 869 tests, 0 failures
- **master HEAD:** 0e5a23d ✅
- **Tags cerrados:** U1 a U6 + T + T2 + R + R2

---

## Cola de trabajo (post-U6)

### MVP finalizado 🟢
- Todos los tags V24D6 cerrados y pusheados
- Motor V24 realista con λ en target [1.0, 1.5]
- Seed La Liga con 20 equipos reales (~406 jugadores)
- Smoke GO con 869 tests passing
- **MVP JUGABLE: 97%**

### Pendiente post-MVP (para siguiente fase V24D7)
1. **HTTP/E2E tests** — ~40% de cobertura, deuda técnica identificada
2. **Match detail UI polish** — 85%, últimos detalles
3. **Career mutations edge cases** — 90%, algunos edge cases pendientes
4. **Master sync definitivo** — confirmado con U6

---

## Estimación de progreso MVP

| Área | % | Comentario |
|---|---|---|
| V24 engine | 100% | U4 pusheado, λ empírico en target |
| Career mutations | 90% | Completo MVP, falta edge cases |
| Stats | 90% | Completo MVP |
| Lineups backend | 95% | U1+U2 cierran blockers |
| Lineups frontend | 97% | U3+U3.5+T2 pusheados, banner+color OK |
| Match detail UI | 85% | Disparos, ratings, timeline OK |
| Lifecycle end-of-round | 95% | R2 + T2 cierran suspension decrement |
| Roster depth | 100% | U5 pusheado (La Liga seed, 20 equipos reales) |
| Balance tuning | 100% | U4 pusheado, smoke GO |
| HTTP/E2E tests | 40% | Deuda técnica, no blocker |

**MVP jugable estimado: 97%** — todos los tags cerrados y pusheados, motor realista, seed con data real, sin bugs críticos. GO confirmado.

---

## Decisiones arrastradas (no romper)

- NO tocar `application.yaml`, `application-local.yml`, `application-v24-mutations.yml`
- NO reset, NO clean, NO force-push
- Push solo con autorización explícita del usuario
- Reportes en `.md` vía `cat <<EOF` o python (una sola escritura, no edición incremental)
- Mensajes de commit en inglés conventional; planes y reportes en español

## Branches en remote (informativo)
- `master` ← release limpia con MVP completo
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