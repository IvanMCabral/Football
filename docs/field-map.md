# Field Map — Sprint C15 (V25D54 formations-reality)

**Sprint:** V25D54-C15 (Formations reality fix)
**Branch:** `V25D54-BACK-FORMATIONS-REALITY`
**Tag (post-merge):** `release-V25D54-BACK`
**Scope:** back services (FormationService + Formation enum + DetailedFormationParser).
**Status:** Implementation complete. 12 formations expuestas, role labels correctos.

> Supersedes the C14 audit doc (`docs/field-map.md` pre-C15). The C14
> research findings (gap analysis, role mislabels, missing formations)
> are the basis for the C15 fixes applied here. See
> `docs/sprint-C14-report` (predecessor sprint) for the original audit.

---

## 0. TL;DR

- The back exposes **82 subdivisions** (81 normales + 1 GK) via `FieldSubdivisionService` and **12 formations** via `FormationService` (was 7 pre-C15).
- **P0 fixed:** 3-5-2 y 3-4-3 ahora tienen role labels correctos para los wide mids (LWB/RWB en lugar de LM/RM). Las coordenadas no cambiaron.
- **P1 added:** 4 formations nuevas (3-5-2-CDM, 5-4-1, 3-4-1-2, 4-2-2-2) — todas feature requests de Iván.
- **P2 added:** variante 4-3-3-1 con pivote CDM (mantiene 4-3-3 flat para compat).
- Cobertura del grid sigue en **31.7%** (26/82) — las 5 formations nuevas reutilizan los mismos subdivisionIds, no agregan nuevos slots al grid.
- Engine Detailed ahora entiende las 5 formations nuevas via `DetailedFormationParser` (casos explícitos + fallback numérico).
- Front `formation-modal` todavía muestra labels genéricos (DF/MD/AT) — el fix P3.2 está en otro sprint (branch front).

---

## 1. Grilla completa del campo

(Sin cambios respecto al C14 — sigue siendo 82 subdivisiones, 26 usadas.)

### 1.1 Estructura

| Atributo | Valor | Origen |
|---|---|---|
| Sectores | 27 (3 columnas × 9 filas) | `SECTORS_PER_ROW=3`, `TOTAL_ROWS=9` |
| Sub-espacios por sector | 3 (subIndex 1=left, 2=mid, 3=right) | línea 116 |
| Subdivisiones normales | 81 (27 × 3) | `TOTAL_NORMAL_SUBDIVISIONS` |
| Slot GK | 1 (separado del sector 26) | `GK_SECTOR=26`, `subdivisionId="GK-1"` |
| **Total** | **82 subdivisiones** | `TOTAL_SUBDIVISIONS` |

### 1.2 Convenciones de IDs y coordenadas

| Campo | Patrón | Rango | Notas |
|---|---|---|---|
| `subdivisionId` | `S{NN}-{subIndex}` (normales) o `GK-1` | sector 01–27, subIndex 1–3 | `String.format("S%02d-%d", sector, subIndex)` |
| `left` | `(sectorCol * 3 + (subIndex - 1)) * 11.11` | 0.00 – 88.89 | `SUB_WIDTH=11.11` |
| `top` | `sectorRow * 11.11` | 0.00 – 88.89 | `SUB_HEIGHT=11.11` |
| `width` × `height` (normales) | 11.11 × 11.11 | — | sub-espacio cuadrado |
| GK `left/top/width/height` | 35.0 / 88.0 / 30.0 / 10.0 | dentro de [0,100] | slot grande que overlapea sector 26 |
| `zone` | `ATTACK` (row 0-1), `MIDFIELD` (row 2-5), `DEFENSE` (row 6-8), `GK` | — | `zoneForRow()` |

**Orientación:** `top% bajo` = zona ATAQUE (parte superior de la pantalla); `top% alto` = zona DEFENSA (parte inferior). El GK está en `top=88` (parte inferior).

### 1.3 Mapa por filas (los 82 slots)

(Sin cambios respecto al C14. Ver sección 1.3 del C14 report original para el mapa detallado.)

### 1.4 Resumen de cobertura

| Row | zone | Slots totales | Slots usados | Vacíos |
|---|---|---|---|---|
| 0 | ATTACK | 9 | 1 | 8 |
| 1 | ATTACK | 9 | 4 | 5 |
| 2 | MIDFIELD | 9 | 0 | 9 |
| 3 | MIDFIELD | 9 | 3 | 6 |
| 4 | MIDFIELD | 9 | 7 | 2 |
| 5 | MIDFIELD | 9 | 5 | 4 |
| 6 | DEFENSE | 9 | 0 | 9 |
| 7 | DEFENSE | 9 | 5 | 4 |
| 8 | DEFENSE | 9 | 0 | 9 (3 ocultos bajo GK) |
| GK | GK | 1 | 1 | 0 |
| **TOTAL** | | **82** | **26** | **56** |

**Cobertura: 31.7%** (26/82). **El 68% de los slots no se usan por ninguna formation.**

> **V25D54-C15:** las 5 formations nuevas (3-5-2-CDM, 5-4-1, 3-4-1-2,
> 4-2-2-2, 4-3-3-1) reusan subdivisionIds del set de 26 ya cubierto.
> Por eso la cobertura sigue en 31.7% — los fixes son de **shape
> label** y de **feature variants**, no de **nuevos slots del grid**.

---

## 2. Formations actuales (12 en total)

Cada formación expone 11 posiciones (1 GK + 10 outfield) con subdivisionId, role label, x%, y% y actionRange (en %). Las coordenadas `xPercent`/`yPercent` son el **centro visual** del slot (no top-left); el slot real se extiende ±width/2 / ±height/2.

### 2.1 4-4-2 — clásica equilibrada
4 DEF + 4 MID + 2 ATT

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | LB | S22-1 | 11.00 | 83.00 | 7.00 | |
| 2 | CB | S22-2 | 33.00 | 83.00 | 6.00 | |
| 3 | CB | S23-2 | 67.00 | 83.00 | 6.00 | |
| 4 | RB | S24-3 | 89.00 | 83.00 | 7.00 | |
| 5 | LM | S16-1 | 11.00 | 61.00 | 8.00 | wide mid (row 5) |
| 6 | CM | S16-2 | 39.00 | 61.00 | 7.00 | |
| 7 | CM | S17-2 | 61.00 | 61.00 | 7.00 | |
| 8 | RM | S18-3 | 89.00 | 61.00 | 8.00 | wide mid (row 5) |
| 9 | ST | S05-2 | 39.00 | 17.00 | 7.00 | |
| 10 | ST | S05-3 | 61.00 | 17.00 | 7.00 | |

### 2.2 4-3-3 — ofensiva con wingers
4 DEF + 3 MID + 3 ATT (2 wingers + 1 ST)

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | LB | S22-1 | 11.00 | 83.00 | 7.00 | |
| 2 | CB | S22-2 | 33.00 | 83.00 | 6.00 | |
| 3 | CB | S23-2 | 67.00 | 83.00 | 6.00 | |
| 4 | RB | S24-3 | 89.00 | 83.00 | 7.00 | |
| 5 | CM | S13-2 | 30.00 | 50.00 | 8.00 | |
| 6 | CM | S14-2 | 50.00 | 55.00 | 7.00 | |
| 7 | CM | S15-2 | 70.00 | 50.00 | 8.00 | |
| 8 | LW | S04-1 | 11.00 | 17.00 | 7.00 | winger (esquina) |
| 9 | ST | S05-2 | 50.00 | 12.00 | 6.00 | levemente más adelantado |
| 10 | RW | S06-3 | 89.00 | 17.00 | 7.00 | winger (esquina) |

### 2.3 3-5-2 — back-three con 5 mids (P0 fixed)
3 DEF + 5 MID + 2 ATT. **P0 fixed:** pos #4 LM→LWB, pos #8 RM→RWB.

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | CB | S22-1 | 22.00 | 83.00 | 7.00 | LCB |
| 2 | CB | S23-2 | 50.00 | 88.00 | 6.00 | CB central, y más profundo |
| 3 | CB | S24-3 | 78.00 | 83.00 | 7.00 | RCB |
| 4 | **LWB** | S15-1 | 6.00 | 55.00 | 9.00 | **P0 fixed** (era LM) |
| 5 | CM | S16-2 | 30.00 | 61.00 | 7.00 | |
| 6 | CM | S17-2 | 50.00 | 66.00 | 7.00 | |
| 7 | CM | S18-2 | 70.00 | 61.00 | 7.00 | |
| 8 | **RWB** | S18-3 | 94.00 | 55.00 | 9.00 | **P0 fixed** (era RM) |
| 9 | ST | S05-2 | 39.00 | 17.00 | 7.00 | |
| 10 | ST | S05-3 | 61.00 | 17.00 | 7.00 | |

### 2.4 4-2-3-1 — diamond con anchor CDMs
4 DEF + 2 CDM + 3 CAM + 1 ST

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | LB | S22-1 | 11.00 | 83.00 | 7.00 | |
| 2 | CB | S22-2 | 33.00 | 83.00 | 6.00 | |
| 3 | CB | S23-2 | 67.00 | 83.00 | 6.00 | |
| 4 | RB | S24-3 | 89.00 | 83.00 | 7.00 | |
| 5 | CDM | S16-2 | 35.00 | 66.00 | 7.00 | anchor (más profundo) |
| 6 | CDM | S17-2 | 65.00 | 66.00 | 7.00 | anchor |
| 7 | LW | S10-1 | 11.00 | 39.00 | 8.00 | CAM-flank izquierdo |
| 8 | CAM | S11-2 | 50.00 | 39.00 | 8.00 | CAM central (trequartista) |
| 9 | RW | S12-3 | 89.00 | 39.00 | 8.00 | CAM-flank derecho |
| 10 | ST | S02-2 | 50.00 | 6.00 | 6.00 | ST top (y=6, único en row 0) |

### 2.5 5-3-2 — back-five defensiva
5 DEF + 3 CM + 2 ATT

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | LB | S22-1 | 6.00 | 83.00 | 8.00 | |
| 2 | CB | S22-2 | 28.00 | 83.00 | 6.00 | |
| 3 | CB | S23-2 | 50.00 | 86.00 | 6.00 | CB central, y más profundo |
| 4 | CB | S23-3 | 72.00 | 83.00 | 6.00 | |
| 5 | RB | S24-3 | 94.00 | 83.00 | 8.00 | |
| 6 | CM | S16-1 | 25.00 | 61.00 | 7.00 | |
| 7 | CM | S17-2 | 50.00 | 66.00 | 7.00 | |
| 8 | CM | S18-2 | 75.00 | 61.00 | 7.00 | |
| 9 | ST | S05-2 | 35.00 | 17.00 | 7.00 | |
| 10 | ST | S05-3 | 65.00 | 17.00 | 7.00 | |

### 2.6 4-1-4-1 — anchor CDM + 4 wide
4 DEF + 1 CDM + 4 MID (LM/CM/CM/RM) + 1 ST

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | LB | S22-1 | 11.00 | 83.00 | 7.00 | |
| 2 | CB | S22-2 | 33.00 | 83.00 | 6.00 | |
| 3 | CB | S23-2 | 67.00 | 83.00 | 6.00 | |
| 4 | RB | S24-3 | 89.00 | 83.00 | 7.00 | |
| 5 | CDM | S17-2 | 50.00 | 66.00 | 7.00 | anchor único |
| 6 | LM | S13-1 | 11.00 | 50.00 | 8.00 | wide mid (row 4) |
| 7 | CM | S14-2 | 39.00 | 50.00 | 7.00 | |
| 8 | CM | S14-3 | 61.00 | 50.00 | 7.00 | |
| 9 | RM | S15-3 | 89.00 | 50.00 | 8.00 | wide mid (row 4) |
| 10 | ST | S05-2 | 50.00 | 12.00 | 6.00 | |

### 2.7 3-4-3 — back-three ofensiva con wingers (P0 fixed)
3 DEF + 4 MID + 3 ATT (LW/ST/RW). **P0 fixed:** pos #4 LM→LWB, pos #7 RM→RWB.

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | CB | S22-1 | 22.00 | 83.00 | 7.00 | LCB |
| 2 | CB | S23-2 | 50.00 | 88.00 | 6.00 | CB central, y más profundo |
| 3 | CB | S24-3 | 78.00 | 83.00 | 7.00 | RCB |
| 4 | **LWB** | S15-1 | 6.00 | 55.00 | 9.00 | **P0 fixed** (era LM) |
| 5 | CM | S16-2 | 36.00 | 61.00 | 7.00 | |
| 6 | CM | S17-2 | 64.00 | 61.00 | 7.00 | |
| 7 | **RWB** | S18-3 | 94.00 | 55.00 | 9.00 | **P0 fixed** (era RM) |
| 8 | LW | S04-1 | 11.00 | 17.00 | 7.00 | winger (esquina) |
| 9 | ST | S05-2 | 50.00 | 12.00 | 6.00 | |
| 10 | RW | S06-3 | 89.00 | 17.00 | 7.00 | winger (esquina) |

### 2.8 3-5-2-CDM — back-three con 1 CDM pivot (P1.1, NEW)
3 DEF + 1 CDM + 2 CM + 2 WB + 2 ATT. Variante explícita del 3-5-2 con pivote CDM claro.

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | CB | S22-1 | 22.00 | 83.00 | 7.00 | LCB |
| 2 | CB | S23-2 | 50.00 | 88.00 | 6.00 | CB central |
| 3 | CB | S24-3 | 78.00 | 83.00 | 7.00 | RCB |
| 4 | CDM | S17-2 | 50.00 | 72.00 | 8.00 | anchor (entre DEF y MID) |
| 5 | CM | S16-2 | 30.00 | 61.00 | 7.00 | |
| 6 | CM | S18-2 | 70.00 | 61.00 | 7.00 | |
| 7 | LWB | S15-1 | 6.00 | 55.00 | 9.00 | carril izquierdo |
| 8 | RWB | S18-3 | 94.00 | 55.00 | 9.00 | carril derecho |
| 9 | ST | S05-2 | 39.00 | 17.00 | 7.00 | |
| 10 | ST | S05-3 | 61.00 | 17.00 | 7.00 | |

### 2.9 5-4-1 — back-five ultra-defensiva (P1.2, NEW)
5 DEF + 4 MID + 1 ST. Common en copas.

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | LB | S22-1 | 6.00 | 83.00 | 8.00 | |
| 2 | CB | S22-2 | 28.00 | 83.00 | 6.00 | |
| 3 | CB | S23-2 | 50.00 | 86.00 | 6.00 | CB central, y más profundo |
| 4 | CB | S23-3 | 72.00 | 83.00 | 6.00 | |
| 5 | RB | S24-3 | 94.00 | 83.00 | 8.00 | |
| 6 | LM | S16-1 | 15.00 | 61.00 | 7.00 | wide mid izquierdo |
| 7 | CM | S16-2 | 38.00 | 66.00 | 7.00 | |
| 8 | CM | S17-2 | 62.00 | 66.00 | 7.00 | |
| 9 | RM | S18-3 | 85.00 | 61.00 | 7.00 | wide mid derecho |
| 10 | ST | S05-2 | 50.00 | 17.00 | 7.00 | único ST |

### 2.10 3-4-1-2 — Christmas tree (P1.3, NEW)
3 DEF + 4 MID (LWB + 2 CM + RWB) + 1 CAM + 2 ST.

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | CB | S22-1 | 22.00 | 83.00 | 7.00 | LCB |
| 2 | CB | S23-2 | 50.00 | 88.00 | 6.00 | CB central |
| 3 | CB | S24-3 | 78.00 | 83.00 | 7.00 | RCB |
| 4 | LWB | S15-1 | 6.00 | 55.00 | 9.00 | carril izquierdo |
| 5 | CM | S16-2 | 30.00 | 61.00 | 7.00 | |
| 6 | CM | S18-2 | 70.00 | 61.00 | 7.00 | |
| 7 | RWB | S18-3 | 94.00 | 55.00 | 9.00 | carril derecho |
| 8 | CAM | S11-2 | 50.00 | 39.00 | 8.00 | trequartista |
| 9 | ST | S05-2 | 39.00 | 17.00 | 7.00 | |
| 10 | ST | S05-3 | 61.00 | 17.00 | 7.00 | |

### 2.11 4-2-2-2 — narrow diamond (P1.4, NEW)
4 DEF + 2 CDM + 2 wide mids + 2 ST. Alternativa al 4-4-2 con doble pivote.

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | LB | S22-1 | 11.00 | 83.00 | 7.00 | |
| 2 | CB | S22-2 | 33.00 | 83.00 | 6.00 | |
| 3 | CB | S23-2 | 67.00 | 83.00 | 6.00 | |
| 4 | RB | S24-3 | 89.00 | 83.00 | 7.00 | |
| 5 | CDM | S16-2 | 35.00 | 66.00 | 7.00 | anchor izquierdo |
| 6 | CDM | S17-2 | 65.00 | 66.00 | 7.00 | anchor derecho |
| 7 | LM | S13-1 | 15.00 | 50.00 | 8.00 | wide mid alto izquierdo |
| 8 | RM | S15-3 | 85.00 | 50.00 | 8.00 | wide mid alto derecho |
| 9 | ST | S05-2 | 39.00 | 17.00 | 7.00 | |
| 10 | ST | S05-3 | 61.00 | 17.00 | 7.00 | |

### 2.12 4-3-3-1 — 4-3-3 con pivote CDM (P2, NEW variant)
4 DEF + 1 CDM + 2 CM + 3 ATT (LW + ST + RW). Variante con CDM claro (mantiene 4-3-3 flat).

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | LB | S22-1 | 11.00 | 83.00 | 7.00 | |
| 2 | CB | S22-2 | 33.00 | 83.00 | 6.00 | |
| 3 | CB | S23-2 | 67.00 | 83.00 | 6.00 | |
| 4 | RB | S24-3 | 89.00 | 83.00 | 7.00 | |
| 5 | CDM | S17-2 | 50.00 | 66.00 | 8.00 | anchor (pivote) |
| 6 | CM | S13-2 | 30.00 | 50.00 | 8.00 | |
| 7 | CM | S15-2 | 70.00 | 50.00 | 8.00 | |
| 8 | LW | S04-1 | 11.00 | 17.00 | 7.00 | winger izquierdo |
| 9 | ST | S05-2 | 50.00 | 12.00 | 6.00 | |
| 10 | RW | S06-3 | 89.00 | 17.00 | 7.00 | winger derecho |

---

## 3. Cambios del Sprint C15 (vs C14 audit)

### 3.1 P0 — Role labels correctos en back-three (DONE)

**Problema C14:** 3-5-2 y 3-4-3 etiquetaban wide mids como `LM`/`RM` cuando en realidad juegan como wing-backs (`LWB`/`RWB`). El engine Detailed los trataba correctamente (wingers=0) pero el label visual mentía al usuario.

**Fix C15:**
- 3-5-2 pos #4: `LM` → `LWB` (slot S15-1)
- 3-5-2 pos #8: `RM` → `RWB` (slot S18-3)
- 3-4-3 pos #4: `LM` → `LWB` (slot S15-1)
- 3-4-3 pos #7: `RM` → `RWB` (slot S18-3)

Coordenadas (x/y/subdivisionId/actionRange) **NO cambian** — sólo el role label.

**Tests agregados** (`FormationServiceTest`):
- `formation_3_5_2_usesLwbRwbForWideMids` (P0 regression guard)
- `formation_3_4_3_usesLwbRwbForWideMids` (P0 regression guard)
- `formation_4_4_2_wideMidsRemainLmRm` (control: confirma que 4-4-2 sigue LM/RM)
- `goldenRolesForOriginal7Formations` (golden master: roles esperados para las 7 originales)

### 3.2 P1 — 4 formations nuevas (DONE)

| # | Formation | Layout implementado |
|---|---|---|
| **P1.1** | 3-5-2-CDM | 3 CB + 1 CDM anchor (S17-2) + 2 CM (S16-2/S18-2) + 2 WB (S15-1/S18-3) + 2 ST |
| **P1.2** | 5-4-1 | 5 DEF (LB/CB/CB/CB/RB) + 2 CM (S16-2/S17-2) + 2 LM/RM wide (S16-1/S18-3) + 1 ST |
| **P1.3** | 3-4-1-2 | 3 CB + LWB (S15-1) + 2 CM (S16-2/S18-2) + RWB (S18-3) + 1 CAM (S11-2) + 2 ST |
| **P1.4** | 4-2-2-2 | 4 DEF + 2 CDM (S16-2/S17-2) + LM/RM wide (S13-1/S15-3) + 2 ST |

**Tests agregados** (`FormationServiceTest`):
- `goldenRolesForNew5Formations` (golden master: roles esperados para las 5 nuevas)
- `newFormationsHaveUniqueSubdivisionIdsAndValidCoords` (integrity: 11 unique IDs, coords en [0,100], 1 GK, meta cuadrada)
- `s23TwoIsUsedByNewFormations` (control: todas las nuevas usan S23-2 como CB central)

### 3.3 P2 — Variante 4-3-3-1 con pivote CDM (DONE)

| # | Formation | Layout implementado |
|---|---|---|
| **P2** | 4-3-3-1 | 4 DEF + 1 CDM (S17-2, anchor) + 2 CM (S13-2/S15-2, wide) + LW/ST/RW (S04-1/S05-2/S06-3) |

Mantiene 4-3-3 flat intacto. La notación "4-3-3-1" significa 4-3-3 con un 1 (CDM) en el medio — el engine Detailed la trata como 4-3-3-like (4 DEF + 3 MID + 2 WING + 1 ST) porque el pivote CDM no cambia el shape forward.

### 3.4 DetailedFormationParser — Engine recognition (DONE)

El parser engine-side se extendió con casos explícitos:

| Formation | Método | Estructura retornada |
|---|---|---|
| `3-5-2-CDM` | special-case before int parsing (contiene letras) | (3, 5, 0, 0, 2) — mismo shape que 3-5-2 |
| `5-4-1` | `parseTwoDashes` generic | (5, 4, 0, 0, 1) |
| `3-4-1-2` | `parseThreeDashes` totalMid=5 | (3, 5, 0, 0, 2) |
| `4-2-2-2` | `parseThreeDashes` totalMid=4 | (4, 4, 0, 0, 2) |
| `4-3-3-1` | `parseThreeDashes` 4-3-3-like | (4, 3, 0, 2, 1) — wingers=2 como 4-3-3 |

**Tests agregados** (`DetailedFormationParserTest`):
- `parses_3_5_2_CDM`, `parses_3_4_1_2_christmas_tree`, `parses_4_2_2_2_narrow_diamond`, `parses_4_3_3_1_with_pivot`
- `outfieldPlayersIsTen` extendido para cubrir las 5 nuevas (todas suman 10)

---

## 4. Out of scope (NO aplicado en C15)

- **P4.1** Reducir el grid de 82 a 25 slots. Breaking change a subdivisionIds persistidos — descartado por Iván en C14.
- **P3.1** formation-modal consume subdivisionIds (gran refactor UI). Decidido P3.2 (más chico) en su lugar.
- **P3.2** formation-modal muestra role labels por dot. **Este fix es para el FRONT sprint C15 (branch `V25D54-FRONT-FORMATION-LABELS` separado, no incluido en este doc).**

---

## 5. Cobertura del grid — sin cambios

(Sin cambios respecto al C14. Ver sección 5 del C14 report original.)

26 subdivisionIds usados, 56 vacíos. Lista completa:

**Usados (26):** GK-1, S02-2, S04-1, S05-2, S05-3, S06-3, S10-1, S11-2, S12-3, S13-1, S13-2, S14-2, S14-3, S15-1, S15-2, S15-3, S16-1, S16-2, S17-2, S18-2, S18-3, S22-1, S22-2, S23-2, S23-3, S24-3.

**Las 5 formations nuevas (3-5-2-CDM, 5-4-1, 3-4-1-2, 4-2-2-2, 4-3-3-1) NO agregan subdivisionIds nuevos** — todas reusan slots del set de 26 ya cubierto. Esto se verifica con el golden test `gridCoverageIsTwentySixUniqueSubdivisionIds` (sigue pasando en C15 sin cambios).

---

## 6. Tests agregados en Sprint C15

C15 agrega los siguientes tests (todos pasan):

### `FormationServiceTest` (C15 nuevos)
1. `goldenRolesForOriginal7Formations` — golden master de roles esperados para 4-4-2, 4-3-3, 3-5-2, 4-2-3-1, 5-3-2, 4-1-4-1, 3-4-3 (P0 verification).
2. `goldenRolesForNew5Formations` — golden master para 3-5-2-CDM, 5-4-1, 3-4-1-2, 4-2-2-2, 4-3-3-1 (P1+P2 verification).
3. `formation_3_5_2_usesLwbRwbForWideMids` — regression guard P0 (3-5-2 wide mids son LWB/RWB).
4. `formation_3_4_3_usesLwbRwbForWideMids` — regression guard P0 (3-4-3 wide mids son LWB/RWB).
5. `formation_4_4_2_wideMidsRemainLmRm` — control (4-4-2 wide mids siguen LM/RM — no afectados por P0).
6. `newFormationsHaveUniqueSubdivisionIdsAndValidCoords` — integrity check (11 unique IDs, coords en [0,100], 1 GK, meta cuadrada).
7. `s23TwoIsUsedByNewFormations` — control (las 5 nuevas usan S23-2 como CB central).

### `DetailedFormationParserTest` (C15 nuevos)
1. `parses_3_5_2_CDM` — engine recognition (3 DEF + 5 MID + 2 ST).
2. `parses_3_4_1_2_christmas_tree` — engine recognition (3 DEF + 5 MID + 2 ST, mid fold).
3. `parses_4_2_2_2_narrow_diamond` — engine recognition (4 DEF + 4 MID + 2 ST, mid fold).
4. `parses_4_3_3_1_with_pivot` — engine recognition (4 DEF + 3 MID + 2 WING + 1 ST, 4-3-3-like).

### Tests modificados (C15)
- `returnsExactly7Formations` → `returnsExactly12Formations` (count 7→12).
- `allExpectedFormationsPresent` — extended con 5 nombres nuevos.
- `getFormationByNameReturnsCorrect` — extended con 5 nombres nuevos.
- `s23TwoIsUsedByAllFormations` — displayName actualizado (7→12 formations).

---

## 7. References

- Back: `src/main/java/com/footballmanager/application/service/editor/FieldSubdivisionService.java`
- Back: `src/main/java/com/footballmanager/application/service/editor/FormationService.java`
- Back: `src/main/java/com/footballmanager/domain/model/valueobject/Formation.java` (enum 12 formations)
- Back: `src/main/java/com/footballmanager/application/service/simulation/detailed/DetailedFormationParser.java`
- Back: `src/main/java/com/footballmanager/domain/model/valueobject/PositionEffectivenessCalculator.java`
- Tests: `src/test/java/com/footballmanager/application/service/editor/FormationServiceTest.java`
- Tests: `src/test/java/com/footballmanager/application/service/simulation/detailed/DetailedFormationParserTest.java`
- Front: `front-ciber/.../components/squad-editor-modal/squad-editor-modal.component.ts` (pre-match, consume subdivisionIds)
- Front: `front-ciber/.../features/games/components/formation-modal/formation-modal.component.ts` (live-match, no consume subdivisionIds) — fix P3.2 en branch front separado
- DTO: `FieldSubdivisionDTO.java`, `FormationDTO.java`, `FormationPositionDTO.java`
- Predecessor: `docs/field-map.md` (C14 audit doc, superseded by este doc)
