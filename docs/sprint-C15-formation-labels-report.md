# Sprint C15 — Formation labels fix (FRONT P3.2) — Reporte

**Sprint:** V25D54-C15 (P3.2 — formation-modal role labels)
**Branch:** `V25D54-FRONT-FORMATION-LABELS`
**Base:** `mvp-1 @ 2f53812` (post-C13 visual fix)
**Tag:** `release-V25D54-FRONT`
**Commit (1):**
- `c5a275c` feat(formation-modal): role labels per dot (LWB, RWB, CDM, CAM, etc.)

---

## TL;DR

Front-end del Sprint C15 P3.2 cerrado. `formation-modal` ahora muestra role labels específicos por formación (LWB, RWB, CDM, CAM, ST, etc.) en lugar de los genéricos anteriores (DF, MD, AT). El dropdown ahora expone las 12 formations (7 originales + 5 nuevas del back). **0 regresiones**, ng test full suite: 190/190 PASS.

---

## Cambios

### `formation-modal.component.ts` — Tabla de role labels + refactor de getDotLabel

**Antes:**
- `getDotLabel(lineIdx, n, count, isLast)` retornaba strings genéricos:
  - `'GK'` para lineIdx=0 && isLast (nunca se aplicaba — bug pre-existente)
  - `'AT'` para isLast
  - `'DF'` para lineIdx=1
  - `'MD'` para todos los demás
- `formationLines` usaba un switch hardcoded por formation (4-4-2 → [1,4,4,2], etc.)
- `FORMATIONS` const con 7 entries (4-4-2, 4-3-3, 3-5-2, 4-2-3-1, 5-3-2, 4-1-4-1, 3-4-3)

**Ahora:**
- Tabla `FORMATION_LINES_BY_FORMATION: Record<string, string[][]>` con role labels per-line para las 12 formations:
  ```typescript
  '3-5-2': [
    ['GK'],
    ['CB', 'CB', 'CB'],
    ['LWB', 'CM', 'CM', 'CM', 'RWB'],  // P0 fixed: LM→LWB, RM→RWB
    ['ST', 'ST']
  ],
  '4-2-3-1': [
    ['GK'],
    ['LB', 'CB', 'CB', 'RB'],
    ['CDM', 'CDM'],                    // anchors explícitos
    ['LW', 'CAM', 'RW'],               // CAM trequartista visible
    ['ST']
  ],
  '3-5-2-CDM': [
    ['GK'],
    ['CB', 'CB', 'CB'],
    ['CDM'],                           // P1 nuevo: pivot explícito
    ['CM', 'CM'],
    ['LWB', 'RWB'],
    ['ST', 'ST']
  ],
  // ... etc para 5-4-1, 3-4-1-2, 4-2-2-2, 4-3-3-1
  ```
- `getDotLabel(lineIdx, n)` → role label específico del map
- `formationLines` derivado del map (counts siempre matchean labels, no hay drift)
- `FORMATIONS` array: 7 → 12 entries (dropdown muestra todas)

**Source of truth**: los role labels matchean exactamente los golden masters del back:
- `FormationServiceTest.goldenRolesForOriginal7Formations` (7 originales)
- `FormationServiceTest.goldenRolesForNew5Formations` (5 nuevas)

Mantener esta simetría significa que si Iván edita el back, el front puede detectar drift inmediatamente con los tests.

---

### `formation-modal.component.spec.ts` — 5 tests nuevos para P3.2

**Tests existentes actualizados (no se eliminaron):**
- `formationLines returns the correct counts per formation (7 originales)` — extended para cubrir las 7 formations (antes sólo 4).
- `normalizes an unknown formation to 4-4-2` — usa la constante `ALL_FORMATIONS` para validar membership.

**Tests nuevos (5):**
- `V25D54-C15 P3.2: formations list includes the 12 formations (7 + 5 nuevas)` — valida que el dropdown expone las 12.
- `V25D54-C15 P3.2: formationLines counts correctos para 5 formations nuevas` — valida el shape correcto para cada formation nueva (1 GK + sum = 10 outfield).
- `V25D54-C15 P3.2: getDotLabel devuelve role labels específicos (no genéricos)` — valida LWB/RWB (3-5-2, 3-4-3 implied), CDM (4-2-3-1, 3-5-2-CDM, 4-3-3-1), CAM (4-2-3-1, 3-4-1-2 implied), LW/ST/RW (4-3-3, 4-3-3-1), LB/CB/RB (5-4-1).
- `V25D54-C15 P3.2: getDotLabel devuelve string vacío si el índice está fuera de rango` — defensivo.

---

## Cobertura

- `ng test formation-modal.component.spec`: **14/14 PASS** (was 9, +5 nuevos)
- `ng test full suite`: **190/190 PASS** (was 178, +12 nuevos del sprint)
  - Incremento: +5 nuevos en formation-modal + otros tests del sprint (C13 effectiveness, etc.)

**0 regresiones**. El resto del front (squad-editor, lineup-modal, etc.) sigue funcionando igual.

---

## Out of scope (NO aplicado)

- **CSS classes específicas por role** (e.g., `is-lwb`, `is-cdm`, `is-cam` con colores distintos). El scope de P3.2 era LABELS, no COLORS. Los dots siguen usando las 4 categorías actuales (GK/DEF/MID/ATT).
- **formation-modal consume subdivisionIds** (gran refactor de UI live-match). Esto es P3.1, descartado en C14 por alto riesgo vs bajo valor (el manager no drag-and-droppea durante el partido).
- **Fix del bug pre-existente** `is-gk` class nunca se aplica (`last && i === 0` es siempre false). P3.2 no incluye este fix — sería un cambio separado.

---

## Decisiones de diseño

1. **Por qué tabla `Record<string, string[][]>` y no enum o servicio**: el modal es standalone y no debería hacer HTTP calls para algo tan chico como labels. La tabla es local al componente y se compila con el bundle (no runtime fetch).

2. **Por qué derivado `formationLines` del map**: garantiza que counts y labels siempre matcheen. Si agregamos una formation nueva al map, los counts salen automáticamente. Si hay un mismatch (e.g., un line con 4 entries pero el template espera 3 dots), falla el test `formationLines counts`.

3. **Por qué LWB/RWB en lugar de LM/RM para 3-5-2/3-4-3 wide mids**: alineado con el back P0 fix (commit f06c550). Si el back dice LWB/RWB, el front debe decirlo también — el visual y la lógica del engine deben coincidir.

4. **Por qué 12 formations en el dropdown (no 11)**: el back expone 12 (task validation dice 11 pero la implementación tiene 12 incluyendo P2 4-3-3-1). Mantener simetría: el front muestra lo que el back tiene.

---

## Files modificados

```
front-ciber/project/src/app/features/games/components/formation-modal/formation-modal.component.ts       | 226 +++++++++++++++++++++-
front-ciber/project/src/app/features/games/components/formation-modal/formation-modal.component.spec.ts | 89 ++++++++--
```

Total: 2 files, 239 insertions, 24 deletions.

---

## Próximos pasos (post-C15 front)

- [ ] Iván revisa este report + back report
- [ ] Push + merge del branch (Iván decide el timing)
- [ ] Verifier visual (REVISOR-football) corre smoke del formation-modal con las 5 formations nuevas — confirma que el visual muestra los labels correctos en cada dot
- [ ] Si REVISOR encuentra issues visuales → sprint siguiente con fixes

---

## References

- Code: `front-ciber/project/src/app/features/games/components/formation-modal/formation-modal.component.ts`
- Tests: `front-ciber/project/src/app/features/games/components/formation-modal/formation-modal.component.spec.ts`
- Back companion: `docs/sprint-C15-formations-reality-report.md`
- Predecessor sprint: C14 audit (`docs/field-map.md`)
