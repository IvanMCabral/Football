# Sprint C15 — Formations reality fix (BACK) — Reporte

**Sprint:** V25D54-C15 (Formations reality fix)
**Branch:** `V25D54-BACK-FORMATIONS-REALITY`
**Base:** `mvp-1-performance-cleanup @ 8edc778` (post-C14)
**Tag:** `release-V25D54-BACK`
**Commits (2, separados por concern):**
- `f06c550` P0 — fix(formations): correct role labels for 3-5-2/3-4-3 wide mids (LM→LWB, RM→RWB)
- `d6042af` P1+P2 — feat(formations): 5 formations nuevas + V24 parser recognition + golden tests

---

## TL;DR

Back-end del Sprint C15 cerrado. 12 formations expuestas (7 originales + 5 nuevas), role labels correctos para back-three con wing-backs, engine V24 reconoce las 5 formations nuevas. **0 fallos, 0 regresiones**, 19 tests nuevos en suites específicas (más los golden tests que se actualizaron de 7→12 formations). mvn test full suite: 2067 tests, 0 failures, 127 errors (todos Redis baseline — no relacionado).

---

## Cambios por concern

### P0 — Role labels correctos en back-three (commit f06c550)

**Problema (audit C14):** 3-5-2 y 3-4-3 etiquetaban sus wide mids como `LM`/`RM` cuando en realidad juegan como wing-backs. Engine V24 los trataba correctamente (wingers=0) pero el label visual mentía.

**Fix:**
- `FormationService.java` (líneas 119-143, 221-245):
  - 3-5-2 pos #4: `LM` → `LWB` (slot S15-1)
  - 3-5-2 pos #8: `RM` → `RWB` (slot S18-3)
  - 3-4-3 pos #4: `LM` → `LWB` (slot S15-1)
  - 3-4-3 pos #7: `RM` → `RWB` (slot S18-3)
- Coordenadas (x/y/subdivisionId/actionRange) **NO cambian** — sólo role label.

**Tests (3 nuevos en `FormationServiceTest`):**
- `formation_3_5_2_usesLwbRwbForWideMids` — regression guard para 3-5-2
- `formation_3_4_3_usesLwbRwbForWideMids` — regression guard para 3-4-3
- `formation_4_4_2_wideMidsRemainLmRm` — control: confirma que 4-4-2 sigue LM/RM (no afectado por P0)

**Resultado:**
- `mvn test FormationServiceTest` → 18/18 PASS (was 15, +3 P0 tests)

---

### P1 — 4 formations nuevas (commit d6042af)

**Problema (audit C14):** 4 formations que Iván pidió no existían: 3-5-2 con CDM, 5-4-1, 3-4-1-2, 4-2-2-2.

**Fix:**

| # | Formation | Layout implementado |
|---|---|---|
| **P1.1** | 3-5-2-CDM | 3 CB (S22-1, S23-2, S24-3) + 1 CDM (S17-2 anchor @ y=72) + 2 CM (S16-2, S18-2) + 2 WB (S15-1, S18-3) + 2 ST (S05-2, S05-3) |
| **P1.2** | 5-4-1 | 5 DEF (S22-1, S22-2, S23-2, S23-3, S24-3) + 2 CM (S16-2, S17-2) + 2 LM/RM wide (S16-1, S18-3) + 1 ST (S05-2) |
| **P1.3** | 3-4-1-2 (Christmas tree) | 3 CB (S22-1, S23-2, S24-3) + LWB (S15-1) + 2 CM (S16-2, S18-2) + RWB (S18-3) + 1 CAM (S11-2 @ row 3) + 2 ST (S05-2, S05-3) |
| **P1.4** | 4-2-2-2 (narrow diamond) | 4 DEF (S22-1, S22-2, S23-2, S24-3) + 2 CDM (S16-2, S17-2 @ row 5) + LM/RM (S13-1, S15-3 @ row 4) + 2 ST (S05-2, S05-3) |

**Cambios en código:**
- `Formation.java` enum: +5 entries (cada una cumple invariant `def + mid + att = 10`)
- `FormationService.buildFormations()`: 5 nuevos bloques con coords validadas (no overlap, [0,100], S23-2 como CB central en todas)
- `V24FormationParser`: 5 casos explícitos para engine recognition (ver P1+P2 detalle abajo)

### P2 — Variante 4-3-3-1 con pivote CDM (commit d6042af)

**Problema (audit C14):** 4-3-3 con pivote CDM claro no existía; los managers querían diferenciar la variante flat de la variante con anchor.

**Fix:**
- `4-3-3-1` (4 DEF + 1 CDM + 2 CM + 3 ATT) agregado como variant nueva, manteniendo `4-3-3` flat intacto (compat total).
- Engine V24 la trata como 4-3-3-like (4 DEF + 3 MID + 2 WING + 1 ST) porque el pivote CDM no cambia el shape forward.

**Layout:**
- 4 DEF (S22-1, S22-2, S23-2, S24-3)
- 1 CDM (S17-2 anchor @ y=66)
- 2 CM (S13-2, S15-2 wide @ row 4)
- LW/ST/RW (S04-1, S05-2, S06-3) — mismas wings que 4-3-3

---

### V24FormationParser — Engine recognition (commit d6042af)

**Cambio crítico**: parser extendido con casos explícitos para las 5 formations nuevas. Engine V24 ahora las entiende.

| Formation | Método | Estructura retornada |
|---|---|---|
| `3-5-2-CDM` | special-case BEFORE int parse (contiene letras) | `(3, 5, 0, 0, 2)` — same shape as 3-5-2 |
| `5-4-1` | `parseTwoDashes` generic | `(5, 4, 0, 0, 1)` |
| `3-4-1-2` | `parseThreeDashes` totalMid=5 | `(3, 5, 0, 0, 2)` |
| `4-2-2-2` | `parseThreeDashes` totalMid=4 | `(4, 4, 0, 0, 2)` |
| `4-3-3-1` | `parseThreeDashes` 4-3-3-like | `(4, 3, 0, 2, 1)` — wingers=2 como 4-3-3 |

**Bug fix encontrado durante testing**: `3-5-2-CDM` contiene letras, entonces `Integer.parseInt("CDM")` tira NumberFormatException ANTES de llegar al `if` específico. Solución: special-case `3-5-2-CDM` ANTES de intentar parsear los ints.

**Tests (4 nuevos en `V24FormationParserTest`):**
- `parses_3_5_2_CDM` — engine recognition (3 DEF + 5 MID + 2 ST)
- `parses_3_4_1_2_christmas_tree` — engine recognition (3 DEF + 5 MID + 2 ST, mid fold)
- `parses_4_2_2_2_narrow_diamond` — engine recognition (4 DEF + 4 MID + 2 ST, mid fold)
- `parses_4_3_3_1_with_pivot` — engine recognition (4 DEF + 3 MID + 2 WING + 1 ST, 4-3-3-like)
- `outfieldPlayersIsTen` extendido para cubrir las 5 nuevas (todas suman 10)

---

### `FormationServiceTest` — Golden tests actualizados + nuevos (commit d6042af)

**Tests existentes actualizados (count=7→12):**
- `returnsExactly7Formations` → `returnsExactly12Formations` (7 → 12 formations)
- `allExpectedFormationsPresent` — extended con 5 nombres nuevos (3-5-2-CDM, 5-4-1, 3-4-1-2, 4-2-2-2, 4-3-3-1)
- `getFormationByNameReturnsCorrect` — extended con 5 nombres nuevos
- `s23TwoIsUsedByAllFormations` — displayName actualizado (7→12 formations)

**Tests nuevos:**
- `goldenRolesForOriginal7Formations` — golden master de role labels para 4-4-2, 4-3-3, 3-5-2, 4-2-3-1, 5-3-2, 4-1-4-1, 3-4-3
- `goldenRolesForNew5Formations` — golden master para 3-5-2-CDM, 5-4-1, 3-4-1-2, 4-2-2-2, 4-3-3-1
- `newFormationsHaveUniqueSubdivisionIdsAndValidCoords` — integrity check (11 unique IDs, coords en [0,100], 1 GK, meta cuadrada)
- `s23TwoIsUsedByNewFormations` — control (las 5 nuevas usan S23-2 como CB central)

**Tests C14 sin cambios (PASA sin tocar):**
- `gridCoverageIsTwentySixUniqueSubdivisionIds` — sigue siendo 26 (las 5 nuevas reusan subdivisionIds del set de 26)
- `gridGapIsFiftySixUnusedSubdivisionIds` — sigue siendo 56
- `positionsDoNotOverlapWithinFormation` — sigue siendo true (validé a mano las coords de las nuevas)

---

## Cobertura del grid — sin cambios

26/82 = 31.7% (sin cambios respecto al C14). Las 5 formations nuevas reutilizan subdivisionIds del set de 26 ya cubierto:
- 3-5-2-CDM reusa: GK-1, S22-1, S23-2, S24-3, S17-2, S16-2, S18-2, S15-1, S18-3, S05-2, S05-3
- 5-4-1 reusa: GK-1, S22-1, S22-2, S23-2, S23-3, S24-3, S16-1, S16-2, S17-2, S18-3, S05-2
- 3-4-1-2 reusa: GK-1, S22-1, S23-2, S24-3, S15-1, S16-2, S18-2, S18-3, S11-2, S05-2, S05-3
- 4-2-2-2 reusa: GK-1, S22-1, S22-2, S23-2, S24-3, S16-2, S17-2, S13-1, S15-3, S05-2, S05-3
- 4-3-3-1 reusa: GK-1, S22-1, S22-2, S23-2, S24-3, S17-2, S13-2, S15-2, S04-1, S05-2, S06-3

Total unique subdivisionIds usados sigue siendo 26. Los fixes son de **shape label** y de **feature variants**, no de **nuevos slots del grid**.

---

## Validation criteria — estado

| Criterion | Estado |
|---|---|
| mvn test 2056+ sin regresión | ✅ **2067 tests, 0 failures** (was 2048, +19 C15 tests). 127 errors son todos baseline Redis no conectado (sin cambio vs pre-C15). |
| Field map actualizado | ✅ Regenerado en `docs/field-map.md` con 12 formations + P0 changes + P1+P2 details |
| API shape: GET /api/v1/career/formations retorna 11 formations (7 + 4 nuevas) | ⚠️ **Discrepancia menor**: el task dice 11, implementación retorna 12 (incluye P2 variante 4-3-3-1). Decisión: el task trata P2 como "variant" no "formation nueva" pero el código la agrega como entry nueva en `FormationService.getAllFormations()` (mantiene 4-3-3 flat intacto). Si Iván quiere tratar P2 como flag de 4-3-3 (no entry separada), se puede refactorizar — pero el diseño actual es más limpio (dropdown muestra "4-3-3-1" como opción). |
| No regressions: squad-management component sigue mostrando las 7 formations originales correctamente | ✅ `allExpectedFormationsPresent` valida que las 7 originales siguen ahí. |

---

## Files modificados

```
docs/field-map.md                                  | 442 +++++++++------------ (regenerado)
src/main/java/com/footballmanager/application/service/editor/FormationService.java           | 174 ++++++-
src/main/java/com/footballmanager/application/service/simulation/v24/V24FormationParser.java | 43 ++-
src/main/java/com/footballmanager/domain/model/valueobject/Formation.java                    | 7 + (5 entries)
src/test/java/com/footballmanager/application/service/editor/FormationServiceTest.java       | 217 ++++++++-
src/test/java/com/footballmanager/application/service/simulation/v24/V24FormationParserTest.java | 71 ++++
```

Total: 6 files, 599 insertions, 280 deletions (commit d6042af includes the field-map.md regen)

---

## Notas / Decisiones

1. **Por qué 2 commits**: separé P0 (cambio cosmético/semántico chico) de P1+P2 (feature grande: nuevas formations). P0 commit es atómico (sólo labels); P1+P2 commit es atómico (nuevas formations + parser + tests + docs). Si Iván quiere revertir P1+P2 por algún motivo, P0 queda como fix seguro.

2. **Por qué "3-5-2-CDM" y no "3-5-2-1"**: el task acepta ambos. Elegí "-CDM" porque es más explícito (vs "-1" que es ambiguo: ¿1 CDM? ¿1 forward?). Engine parser usa string match así que el nombre es indiferente al engine.

3. **Por qué 4-3-3-1 como entry separada (no flag de 4-3-3)**: el task pregunta explícitamente "¿mantener 4-3-3 flat y agregar 4-3-3-1 como variant nueva? Sí". Mantener ambas como entries independientes es más limpio (dropdown muestra ambas, FormationInferer las cuenta por separado, engine V24 las trata con la misma estructura pero las puede distinguir por nombre si necesita).

4. **V24FormationParser `3-5-2-CDM` special-case**: el parser intenta `Integer.parseInt(parts[3])` que tira NumberFormatException antes de llegar al `if` específico. Solución: check del string ANTES del try-catch de int parsing. Es feo pero funciona y está documentado en el Javadoc.

5. **Cobertura del grid NO cambia**: confirmado por los golden tests C14 (gridCoverageIsTwentySixUniqueSubdivisionIds, gridGapIsFiftySixUnusedSubdivisionIds) que pasan sin tocar. Las 5 formations nuevas reusan slots ya cubiertos.

6. **NO toqué FormationInferer**: el inferer sigue funcionando porque cuenta slots por zone (GK/DEF/MID/ATT) y los nuevos subdivisions ya estaban en zonas correctas. Las nuevas formations se infieren correctamente desde cualquier lineup arbitrario.

---

## Próximos pasos (post-C15)

- [ ] Iván revisa este report + front report
- [ ] Decisión sobre el "11 vs 12 formations" del validation criteria (P2 como entry separada vs flag)
- [ ] Push + merge de los 2 branches (Iván decide el timing)
- [ ] Verifier visual: smoke del formation-modal con las 5 formations nuevas (REVISOR-football)
- [ ] Si Iván quiere P3.1 (formation-modal consume subdivisionIds) → sprint siguiente

---

## References

- Code:
  - `src/main/java/com/footballmanager/application/service/editor/FormationService.java`
  - `src/main/java/com/footballmanager/application/service/simulation/v24/V24FormationParser.java`
  - `src/main/java/com/footballmanager/domain/model/valueobject/Formation.java`
- Tests:
  - `src/test/java/com/footballmanager/application/service/editor/FormationServiceTest.java`
  - `src/test/java/com/footballmanager/application/service/simulation/v24/V24FormationParserTest.java`
- Doc: `docs/field-map.md` (regenerado)
- Predecessor: `docs/field-map.md` (C14 audit, superseded)
