# Field Map — Sprint C14 Audit (research, no implementation)

**Sprint:** V25D53-C14 (research/audit, no fixes)
**Branch:** `V25D53-BACK-FIELD-MAP-AUDIT`
**Tag (post-merge):** `release-V25D53-BACK`
**Scope:** back services only (read-only front research included as appendix).
**Status:** Awaiting Iván's approval before C15 implementation begins.

---

## 0. TL;DR

- The back exposes **82 subdivisions** (81 normales + 1 GK) via `FieldSubdivisionService` and **7 formations** via `FormationService`.
- Of the 82 subdivisionIds, only **26** are referenced by any formation. **56 subdivisionIds are unused** (68%).
- The 3-5-2 labels its wide mids as `LM`/`RM` (midfielders) but they should semantically be `LWB`/`RWB` (wing-backs). Same for 3-4-3's wide mids. This is a ROLE mislabel, not a tactical-engine bug — the V24 parser treats 3-5-2 correctly as 0-wingers + 5-mids, but the FormationService position labels diverge from modern football convention.
- **x/y depth is flat** — every ATT line sits at y≈12–17, every DEF line at y≈83–88, with no staggered depth for "diamond mid" or "high defensive line" variants.
- The front has **two formation UIs**: `squad-editor-modal` (pre-match lineup, consumes all 82 subdivisionIds) and `formation-modal` (live-match formation change, only renders generic GK/DEF/MID/ATT dots — does NOT consume subdivisionIds at all).
- The task lists **4 missing formations** that users regularly request: 3-5-2 with CDM, 5-4-1, 3-4-1-2, 4-2-2-2.

---

## 1. Grilla completa del campo

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

Cada slot es un cuadrado 11.11% × 11.11% (normales) o 30% × 10% (GK). Coordenadas son top-left del rectángulo.

**Leyenda:** ✓ = usado por al menos 1 formation. Vacío = ningún formation lo referencia.

#### Row 0 — `top=0` (ATTACK, sectores 1-3)
| subdivisionId | left | zone | usado por |
|---|---|---|---|
| S01-1 | 0.00 | ATTACK | — |
| S01-2 | 11.11 | ATTACK | — |
| S01-3 | 22.22 | ATTACK | — |
| S02-1 | 33.33 | ATTACK | — |
| **S02-2** | **44.44** | ATTACK | **4-2-3-1 (ST)** |
| S02-3 | 55.56 | ATTACK | — |
| S03-1 | 66.67 | ATTACK | — |
| S03-2 | 77.78 | ATTACK | — |
| S03-3 | 88.89 | ATTACK | — |

#### Row 1 — `top=11.11` (ATTACK, sectores 4-6)
| subdivisionId | left | zone | usado por |
|---|---|---|---|
| **S04-1** | 0.00 | ATTACK | **4-3-3 (LW), 3-4-3 (LW)** |
| S04-2 | 11.11 | ATTACK | — |
| S04-3 | 22.22 | ATTACK | — |
| S05-1 | 33.33 | ATTACK | — |
| **S05-2** | **44.44** | ATTACK | **4-4-2 (ST), 4-3-3 (ST), 3-5-2 (ST), 5-3-2 (ST), 4-1-4-1 (ST), 3-4-3 (ST)** |
| **S05-3** | **55.56** | ATTACK | **4-4-2 (ST), 3-5-2 (ST), 5-3-2 (ST)** |
| S06-1 | 66.67 | ATTACK | — |
| S06-2 | 77.78 | ATTACK | — |
| **S06-3** | **88.89** | ATTACK | **4-3-3 (RW), 3-4-3 (RW)** |

#### Row 2 — `top=22.22` (MIDFIELD, sectores 7-9)
| subdivisionId | left | zone | usado por |
|---|---|---|---|
| S07-1, S07-2, S07-3 | 0/11.11/22.22 | MIDFIELD | — |
| S08-1, S08-2, S08-3 | 33.33/44.44/55.56 | MIDFIELD | — |
| S09-1, S09-2, S09-3 | 66.67/77.78/88.89 | MIDFIELD | — |

→ **9 slots vacíos en row 2** (toda la línea horizontal central está inutilizada).

#### Row 3 — `top=33.33` (MIDFIELD, sectores 10-12)
| subdivisionId | left | zone | usado por |
|---|---|---|---|
| **S10-1** | 0.00 | MIDFIELD | **4-2-3-1 (LW)** |
| S10-2 | 11.11 | MIDFIELD | — |
| S10-3 | 22.22 | MIDFIELD | — |
| S11-1 | 33.33 | MIDFIELD | — |
| **S11-2** | **44.44** | MIDFIELD | **4-2-3-1 (CAM)** |
| S11-3 | 55.56 | MIDFIELD | — |
| S12-1 | 66.67 | MIDFIELD | — |
| S12-2 | 77.78 | MIDFIELD | — |
| **S12-3** | **88.89** | MIDFIELD | **4-2-3-1 (RW)** |

#### Row 4 — `top=44.44` (MIDFIELD, sectores 13-15)
| subdivisionId | left | zone | usado por |
|---|---|---|---|
| **S13-1** | 0.00 | MIDFIELD | **4-1-4-1 (LM)** |
| **S13-2** | **11.11** | MIDFIELD | **4-3-3 (CM)** |
| S13-3 | 22.22 | MIDFIELD | — |
| S14-1 | 33.33 | MIDFIELD | — |
| **S14-2** | **44.44** | MIDFIELD | **4-3-3 (CM), 4-1-4-1 (CM)** |
| **S14-3** | **55.56** | MIDFIELD | **4-1-4-1 (CM)** |
| **S15-1** | **66.67** | MIDFIELD | **3-5-2 (LM), 3-4-3 (LM)** |
| **S15-2** | **77.78** | MIDFIELD | **4-3-3 (CM)** |
| **S15-3** | **88.89** | MIDFIELD | **4-1-4-1 (RM)** |

#### Row 5 — `top=55.56` (MIDFIELD, sectores 16-18)
| subdivisionId | left | zone | usado por |
|---|---|---|---|
| **S16-1** | 0.00 | MIDFIELD | **4-4-2 (LM), 5-3-2 (CM)** |
| **S16-2** | **11.11** | MIDFIELD | **4-4-2 (CM), 3-5-2 (CM), 4-2-3-1 (CDM), 3-4-3 (CM)** |
| S16-3 | 22.22 | MIDFIELD | — |
| S17-1 | 33.33 | MIDFIELD | — |
| **S17-2** | **44.44** | MIDFIELD | **4-4-2 (CM), 3-5-2 (CM), 4-2-3-1 (CDM), 5-3-2 (CM), 4-1-4-1 (CDM), 3-4-3 (CM)** |
| S17-3 | 55.56 | MIDFIELD | — |
| S18-1 | 66.67 | MIDFIELD | — |
| **S18-2** | **77.78** | MIDFIELD | **3-5-2 (CM), 5-3-2 (CM)** |
| **S18-3** | **88.89** | MIDFIELD | **4-4-2 (RM), 3-5-2 (RM), 3-4-3 (RM)** |

#### Row 6 — `top=66.67` (DEFENSE, sectores 19-21)
| subdivisionId | left | zone | usado por |
|---|---|---|---|
| S19-1, S19-2, S19-3 | 0/11.11/22.22 | DEFENSE | — |
| S20-1, S20-2, S20-3 | 33.33/44.44/55.56 | DEFENSE | — |
| S21-1, S21-2, S21-3 | 66.67/77.78/88.89 | DEFENSE | — |

→ **9 slots vacíos en row 6** (línea entre mid y defensa, sin uso).

#### Row 7 — `top=77.78` (DEFENSE, sectores 22-24)
| subdivisionId | left | zone | usado por |
|---|---|---|---|
| **S22-1** | 0.00 | DEFENSE | **4-4-2 (LB), 4-3-3 (LB), 3-5-2 (CB), 4-2-3-1 (LB), 5-3-2 (LB), 4-1-4-1 (LB), 3-4-3 (CB)** |
| **S22-2** | **11.11** | DEFENSE | **4-4-2 (CB), 4-3-3 (CB), 4-2-3-1 (CB), 5-3-2 (CB), 4-1-4-1 (CB)** |
| S22-3 | 22.22 | DEFENSE | — |
| S23-1 | 33.33 | DEFENSE | — |
| **S23-2** | **44.44** | DEFENSE | **4-4-2 (CB), 4-3-3 (CB), 3-5-2 (CB), 4-2-3-1 (CB), 5-3-2 (CB), 4-1-4-1 (CB), 3-4-3 (CB)** |
| **S23-3** | **55.56** | DEFENSE | **5-3-2 (CB)** |
| S24-1 | 66.67 | DEFENSE | — |
| S24-2 | 77.78 | DEFENSE | — |
| **S24-3** | **88.89** | DEFENSE | **4-4-2 (RB), 4-3-3 (RB), 3-5-2 (CB), 4-2-3-1 (RB), 5-3-2 (RB), 4-1-4-1 (RB), 3-4-3 (CB)** |

#### Row 8 — `top=88.89` (DEFENSE, sectores 25-27)
| subdivisionId | left | zone | usado por |
|---|---|---|---|
| S25-1, S25-2, S25-3 | 0/11.11/22.22 | DEFENSE | — |
| S26-1, S26-2, S26-3 | 33.33/44.44/55.56 | DEFENSE | — (oculto bajo GK) |
| S27-1, S27-2, S27-3 | 66.67/77.78/88.89 | DEFENSE | — |

→ **9 slots vacíos en row 8** (los 3 subs de S26 están ocultos bajo el GK; los otros 6 son redundantes con row 7).

#### GK slot
| subdivisionId | left | top | width | height | zone | usado por |
|---|---|---|---|---|---|---|
| **GK-1** | 35.00 | 88.00 | 30.00 | 10.00 | GK | **las 7 formations** |

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

---

## 2. Formations actuales

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

### 2.3 3-5-2 — back-three con 5 mids
3 DEF + 5 MID + 2 ATT

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | CB | S22-1 | 22.00 | 83.00 | 7.00 | LCB |
| 2 | CB | S23-2 | 50.00 | 88.00 | 6.00 | CB central, y más profundo |
| 3 | CB | S24-3 | 78.00 | 83.00 | 7.00 | RCB |
| 4 | LM | S15-1 | 6.00 | 55.00 | 9.00 | **MISLABEL: debería ser LWB** |
| 5 | CM | S16-2 | 30.00 | 61.00 | 7.00 | |
| 6 | CM | S17-2 | 50.00 | 66.00 | 7.00 | |
| 7 | CM | S18-2 | 70.00 | 61.00 | 7.00 | |
| 8 | RM | S18-3 | 94.00 | 55.00 | 9.00 | **MISLABEL: debería ser RWB** |
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

### 2.7 3-4-3 — back-three ofensiva con wingers
3 DEF + 4 MID (LM/CM/CM/RM) + 3 ATT (LW/ST/RW)

| # | role | subdivisionId | x% | y% | actionRange | Notas |
|---|---|---|---|---|---|---|
| 0 | GK | GK-1 | 50.00 | 93.00 | 5.00 | |
| 1 | CB | S22-1 | 22.00 | 83.00 | 7.00 | LCB |
| 2 | CB | S23-2 | 50.00 | 88.00 | 6.00 | CB central, y más profundo |
| 3 | CB | S24-3 | 78.00 | 83.00 | 7.00 | RCB |
| 4 | LM | S15-1 | 6.00 | 55.00 | 9.00 | **MISLABEL: debería ser LWB** (3-4-3 = 2 WB + 2 CM en el medio) |
| 5 | CM | S16-2 | 36.00 | 61.00 | 7.00 | |
| 6 | CM | S17-2 | 64.00 | 61.00 | 7.00 | |
| 7 | RM | S18-3 | 94.00 | 55.00 | 9.00 | **MISLABEL: debería ser RWB** |
| 8 | LW | S04-1 | 11.00 | 17.00 | 7.00 | winger (esquina) |
| 9 | ST | S05-2 | 50.00 | 12.00 | 6.00 | |
| 10 | RW | S06-3 | 89.00 | 17.00 | 7.00 | winger (esquina) |

---

## 3. Gap analysis (formaciones modernas vs realidad)

### 3.1 ROLE mislabel en back-three con wide mids

**Problema:** En 3-5-2 y 3-4-3, los dos mediocampistas externos (slots #4 y #8 en cada formation) están etiquetados como `LM`/`RM` en `FormationService`. En el fútbol moderno, esos jugadores son **wing-backs** (`LWB`/`RWB`), no wingers.

**Por qué importa:**

1. **PositionEffectivenessCalculator** (`PositionEffectivenessCalculator.java`):
   - `LWB/RWB → DEF` (con multiplicador 0.95 — carrilero flexibility)
   - `LM/RM → MID` (con multiplicador 0.95)
   - Si el jugador asignado al slot es realmente un LWB-typed player (Player.Position.LWB), pero el slot dice `LM`, el back calcula effectiveness con la tabla MID. Funcionalmente da igual (0.95 ambos), pero el log semántico es incorrecto.
2. **V24DetailedMatchEngine** diferencia wingers vs mids para distribución de shots:
   - Línea 553: "4-3-3, 3-4-3: wingers cut inside → more PENALTY_AREA_WIDE shots".
   - En 3-5-2, los "wide mids" NO son wingers (V24 parser `wingers=0`); deberían comportarse como carrileros.
   - El engine usa `parsed.wingers()` para estas decisiones, no los labels de FormationService. Así que el engine está OK. Pero el FormationService queda mintiendo al usuario sobre qué rol ocupa ese slot.
3. **V24PlayerSelector** (línea 161): "WINGER gets high priority in formations with wingers". En 3-5-2 el selector prioriza roles MID (no WINGER), pero el slot dice `LM` (que es MID-type). Coherente por casualidad, pero el label confunde al usuario que mira el lineup.

**Fix propuesto (C15):** Cambiar `LM` → `LWB` y `RM` → `RWB` en las posiciones 4 y 8 de 3-5-2 y 3-4-3. Las coordenadas (x/y/subdivisionId) se mantienen iguales — sólo el label de role cambia.

### 3.2 Variantes de formation faltantes

| Formation | Estado | Por qué falta | Cuándo se pidió |
|---|---|---|---|
| **3-5-2 con CDM** | No existe | Se confunde con la 3-5-2 actual (que tiene 5 mids planos) | Iván 2026-06 (transcript) |
| **5-4-1** | No existe | Variante ultra-defensiva, común en copas | Iván 2026-06 |
| **3-4-1-2** | No existe | "Christmas tree" — 3 DEF + 4 MID + 1 CAM + 2 ST | Iván 2026-06 |
| **4-2-2-2** | No existe | Alternativa narrow diamond al 4-4-2 | Iván 2026-06 |

**Detalle adicional sobre "3-5-2 con CDM":** la actual 3-5-2 ya tiene 5 mids (LM/CM/CM/CM/RM). Una "3-5-2 con CDM" suele significar 3 DEF + 1 CDM + 2 CM + 2 wing-backs + 2 ST. Diferencia: en la variante con CDM, hay un ancla defensiva clara y los dos wing-backs son explícitos (no se confunden con LM/RM). Esto encaja con el fix 3.1.

### 3.3 Posiciones x/y con poca profundidad

Todas las formations tienen una única línea horizontal por zona:

| Zona | row | y% típico | variación |
|---|---|---|---|
| ATT line | 1 (o 0 para ST top de 4-2-3-1) | 12-17 | solo 5 puntos |
| CAM line (4-2-3-1) | 3 | 39 | plana |
| MID line (5-mid) | 5 | 55-66 | rango 11 puntos (4-3-3 más alto) |
| MID line (4-mid) | 4 (4-1-4-1) o 5 (4-4-2) | 50-61 | |
| DEF line | 7 (o 8 para CB central de 3-5-2/3-4-3/5-3-2) | 83-88 | solo 5 puntos |

**No hay variantes con líneas escalonadas** (e.g., "diamond midfield" donde 1 CM está más adelante que los otros 2). Todas las formations son "flat lines".

Tampoco hay variantes de "high defensive line" (e.g., DEF en y=70 en vez de y=83). El campo es demasiado plano.

### 3.4 actionRange casi uniforme

Rangos observados:
- GK: 5
- CB/LB/RB: 6-8
- CM/CDM: 7-8
- LM/RM/LW/RW: 8-9 (más amplio)
- ST: 6-7

**Diferencia chief:** wide mids/wingers tienen actionRange=8-9, centrales 7. Coherente pero podría afinarse más (e.g., CDM con range 10 para "anchor que cubre zona grande"). No es crítico.

### 3.5 Inconsistencias menores

| Detalle | Observación |
|---|---|
| S05-2 (centro ATT row 1) usado por 6/7 formations como ST | Coherente — todos los ST centrales caen ahí |
| S22-2 (CB en row 7 left-mid) usado por 5/7 formations | Funciona, pero los 4-back siempre usan S22-2; los 5-back además usan S23-3 |
| S23-2 (CB en row 7 centro) usado por 7/7 formations | **Único slot usado por TODAS las formations** — single point of failure |
| GK-1 (top=88) overlapa visualmente con sector 26 (top=88.89) | Por diseño — CSS esconde los 3 subs de S26 |
| S15-1 (LM en 3-5-2/3-4-3) está en MIDFIELD zone (row 4) | Inconsistente con `LM` semántico de "wide mid" (otros LM están en row 5). Esto encaja con el fix 3.1 — debería ser LWB en row 4 sí o sí |

### 3.6 Front: dos UIs, una sola consume subdivisionIds

| Componente | Cuándo se abre | subdivisionIds consumidos |
|---|---|---|
| `squad-editor-modal` | Pre-match (línea de "Gestionar") | **Sí** — 81 + 1, drag-and-drop |
| `formation-modal` | Live-match (cambio formación en partido) | **No** — sólo renderiza líneas planas GK/DEF/MID/ATT con dots |

**Gap:** durante un partido, cuando el manager cambia de 4-4-2 a 3-5-2, ve dots genéricos sin role labels ni effectiveness por slot. No hay forma de saber si el nuevo formation tiene wing-backs que requiere LWB-typed players, ni dónde están los "recommended" slots.

**Decisión necesaria para C15:** ¿queremos alinear `formation-modal` con subdivisionIds? Opciones:
- (a) Refactor `formation-modal` para consumir subdivisionIds (gran cambio, alto valor).
- (b) Mantener `formation-modal` simple y agregar una sección "preview" con nombres de roles.
- (c) No tocar front en C15, dejar el gap documentado.

---

## 4. Recomendaciones para C15 (priorizadas)

### P0 — fixes de correctness (bloquean futuras formations)

| # | Fix | Scope | Impacto |
|---|---|---|---|
| **P0.1** | Cambiar `LM`/`RM` → `LWB`/`RWB` en 3-5-2 (pos #4 y #8) | FormationService.buildFormations() | Role label correcto. No toca subdivisionIds ni coords. |
| **P0.2** | Cambiar `LM`/`RM` → `LWB`/`RWB` en 3-4-3 (pos #4 y #7) | FormationService.buildFormations() | Idem. |
| **P0.3** | Tests que validen el role label esperado por formation (golden values) | FormationServiceTest | Atrapa regresiones si alguien edita `buildFormations()`. |

### P1 — formations faltantes (feature requests de Iván)

| # | Formation | Layout propuesto |
|---|---|---|
| **P1.1** | 3-5-2 con CDM (3 DEF + 1 CDM + 2 CM + 2 WB + 2 ST) | CDMs en S16-2, S17-2; CMs en S16-1, S18-3; WBs en S15-1, S18-3. **Decisión:** ¿LM/RM wide mids reemplazados por LWB/RWB wide mids? Si sí, encaja con P0.1. |
| **P1.2** | 5-4-1 (5 DEF + 4 MID + 1 ST) | Back-five en row 7; mids en row 5 (S16-2/S17-2/S18-2 + 1 wide); ST en S05-2. |
| **P1.3** | 3-4-1-2 (3 DEF + 4 MID + 1 CAM + 2 ST) | Back-three en row 7; 4 mids en row 4-5; CAM en row 3 (S11-2); 2 ST en S05-2/S05-3. |
| **P1.4** | 4-2-2-2 (4 DEF + 2 CDM + 2 wide mids + 2 ST) | Back-4 en row 7; CDMs en S16-2/S17-2; wide mids en S16-1/S18-3 (o S13-1/S15-3); 2 ST en S05-2/S05-3. |

Para cada nueva formation:
- Agregar bloque en `FormationService.buildFormations()` con subdivisionIds, role, x%, y%, actionRange.
- Agregar entrada en `Formation` enum (`domain/model/valueobject/Formation.java`).
- Agregar caso en `V24FormationParser` (necesario si engine usa estos datos).
- Tests: 11 unique IDs, no overlap con otros slots usados, role labels correctos.

### P2 — profundidad y detalle

| # | Mejora | Scope | Trade-off |
|---|---|---|---|
| **P2.1** | Variantes con "diamond mid" (e.g., 4-3-3 con 1 CDM en row 5 y 2 CM en row 4) | FormationService + nuevo flag o nueva formation | +1 formation por cada variante = clutter. Mejor: parametrizar y formation variants con flag. |
| **P2.2** | actionRange diferenciado (CDM=10, winger=8, ST=6) | FormationService | Afecta cálculo de efectividad (futuro Sprint). No romper tests existentes que validan ranges. |
| **P2.3** | actionRange por formación (no global) | Ya existe — cada position tiene su actionRangePercent | OK, no requiere cambio. |

### P3 — front (decisión de scope)

| # | Mejora | Scope | Riesgo |
|---|---|---|---|
| **P3.1** | `formation-modal` consume subdivisionIds y muestra role labels + effectiveness | formation-modal.component.ts (gran refactor) | Alto — refactor de UI live-match. Evaluar vs valor. |
| **P3.2** | `formation-modal` muestra sólo role labels por dot (sin subdivisionIds) | formation-modal.component.ts (cambio chico) | Bajo. Sólo agregar labels por línea. |
| **P3.3** | Mantener gap documentado, no tocar front | — | Sin riesgo. Sprint siguiente decide. |

### P4 — coverage del grid (no priorizado)

| # | Idea | Notas |
|---|---|---|
| **P4.1** | ¿Tiene sentido tener 82 slots si sólo 26 se usan? | El grid cubre más espacio del necesario. Considerar reducir a 6×4 = 24 normales + 1 GK = 25, con menos waste. **Riesgo:** cambio breaking a subdivisionIds ya persistidos. |
| **P4.2** | Slots en row 8 (debajo del GK) son visualmente inútiles | Los 3 subs de S26 están ocultos bajo GK. Los otros 6 (S25, S27) están justo arriba del border inferior. |

---

## 5. Apéndice — Cobertura detallada por subdivisionId

26 subdivisionIds usados, 56 vacíos. Lista completa:

**Usados (26):** GK-1, S02-2, S04-1, S05-2, S05-3, S06-3, S10-1, S11-2, S12-3, S13-1, S13-2, S14-2, S14-3, S15-1, S15-2, S15-3, S16-1, S16-2, S17-2, S18-2, S18-3, S22-1, S22-2, S23-2, S23-3, S24-3.

**Vacíos (56), agrupados por row:**
- Row 0 (ATTACK, 8): S01-1, S01-2, S01-3, S02-1, S02-3, S03-1, S03-2, S03-3.
- Row 1 (ATTACK, 5): S04-2, S04-3, S05-1, S06-1, S06-2.
- Row 2 (MIDFIELD, 9): S07-1, S07-2, S07-3, S08-1, S08-2, S08-3, S09-1, S09-2, S09-3.
- Row 3 (MIDFIELD, 6): S10-2, S10-3, S11-1, S11-3, S12-1, S12-2.
- Row 4 (MIDFIELD, 2): S13-3, S14-1.
- Row 5 (MIDFIELD, 4): S16-3, S17-1, S17-3, S18-1.
- Row 6 (DEFENSE, 9): S19-1, S19-2, S19-3, S20-1, S20-2, S20-3, S21-1, S21-2, S21-3.
- Row 7 (DEFENSE, 4): S22-3, S23-1, S24-1, S24-2.
- Row 8 (DEFENSE, 9): S25-1, S25-2, S25-3, S26-1, S26-2, S26-3, S27-1, S27-2, S27-3.

---

## 6. Apéndice — Notas de testing (audit scripts agregados en este sprint)

Este sprint agrega los siguientes tests (todos en `FieldSubdivisionServiceTest` y `FormationServiceTest`):

1. **Formation positions no se solapan dentro de cada formación** — valida que ningún par de jugadores comparte rectángulo visible. Considera actionRange como extent del rectángulo.
2. **Grid subdivision slots no se solapan entre sí** — valida que los 81 slots normales son adyacentes (no overlapping) en la grilla 9×9.
3. **Cobertura del grid: 26/82 used** — golden test que documenta qué subdivisionIds son referenciados. Si alguien agrega formations nuevas, este test pasa; si alguien borra formations accidentalmente, falla con lista clara de qué desapareció.
4. **Gap de formaciones faltantes** — test que verifica que las 7 formations esperadas están presentes (ya existe).

Los tests de `[0, 100]` y de "11 subdivisionIds únicos por formation" ya existían pre-C14 y se mantienen sin cambios.

---

## 7. References

- Back: `src/main/java/com/footballmanager/application/service/editor/FieldSubdivisionService.java`
- Back: `src/main/java/com/footballmanager/application/service/editor/FormationService.java`
- Back: `src/main/java/com/footballmanager/domain/model/valueobject/Formation.java` (enum 7 formations)
- Back: `src/main/java/com/footballmanager/application/service/simulation/v24/V24FormationParser.java`
- Back: `src/main/java/com/footballmanager/domain/model/valueobject/PositionEffectivenessCalculator.java`
- Front: `front-ciber/.../components/squad-editor-modal/squad-editor-modal.component.ts` (pre-match, consume subdivisionIds)
- Front: `front-ciber/.../features/games/components/formation-modal/formation-modal.component.ts` (live-match, no consume subdivisionIds)
- DTO: `FieldSubdivisionDTO.java`, `FormationDTO.java`, `FormationPositionDTO.java`
