package com.footballmanager.domain.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V25D47 (Sprint C11a): unit tests for {@link PositionEffectivenessCalculator}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>All 5 natural categories × 4 slot categories = 20 cells (plus a few extras).
 *   <li>Backward compat: null / blank inputs → 1.0 (no penalty).
 *   <li>Unknown categories → 1.0 (graceful degradation).
 * </ul>
 *
 * <p>Tests follow the 5-category simplification table documented in
 * {@link PositionEffectivenessCalculator} (CB→MID 0.8, WINGER→MID 0.95, etc.).
 */
@DisplayName("PositionEffectivenessCalculator — V25D47 tactical position effectiveness")
class PositionEffectivenessCalculatorTest {

    // ========== GK row ==========

    @Test
    @DisplayName("GK in GK slot → 1.0 (perfect match)")
    void gkInGK() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("GK", "GK"));
    }

    @Test
    @DisplayName("GK in DEF slot → 0.0 (invalid — GK can't play outfield)")
    void gkInDef() {
        assertEquals(0.0, PositionEffectivenessCalculator.effectiveness("GK", "DEF"));
    }

    @Test
    @DisplayName("GK in MID slot → 0.0 (invalid)")
    void gkInMid() {
        assertEquals(0.0, PositionEffectivenessCalculator.effectiveness("GK", "MID"));
    }

    @Test
    @DisplayName("GK in ATT slot → 0.0 (invalid)")
    void gkInAtt() {
        assertEquals(0.0, PositionEffectivenessCalculator.effectiveness("GK", "ATT"));
    }

    // ========== DEF row (CB default) ==========

    @Test
    @DisplayName("DEF in GK slot → 0.0 (invalid — outfield can't play GK)")
    void defInGK() {
        assertEquals(0.0, PositionEffectivenessCalculator.effectiveness("DEF", "GK"));
    }

    @Test
    @DisplayName("DEF in DEF slot → 1.0 (perfect match)")
    void defInDef() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("DEF", "DEF"));
    }

    @Test
    @DisplayName("DEF in MID slot → 0.8 (CB penalty — defending midfielder)")
    void defInMid() {
        assertEquals(0.8, PositionEffectivenessCalculator.effectiveness("DEF", "MID"));
    }

    @Test
    @DisplayName("DEF in ATT slot → 0.4 (severe penalty — no attacking instincts)")
    void defInAtt() {
        assertEquals(0.4, PositionEffectivenessCalculator.effectiveness("DEF", "ATT"));
    }

    // ========== MID row (CM default) ==========

    @Test
    @DisplayName("MID in GK slot → 0.0 (invalid)")
    void midInGK() {
        assertEquals(0.0, PositionEffectivenessCalculator.effectiveness("MID", "GK"));
    }

    @Test
    @DisplayName("MID in DEF slot → 0.85 (CDM-like — holding mid in defense)")
    void midInDef() {
        assertEquals(0.85, PositionEffectivenessCalculator.effectiveness("MID", "DEF"));
    }

    @Test
    @DisplayName("MID in MID slot → 1.0 (perfect match)")
    void midInMid() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("MID", "MID"));
    }

    @Test
    @DisplayName("MID in ATT slot → 0.85 (CAM-like — attacking midfielder)")
    void midInAtt() {
        assertEquals(0.85, PositionEffectivenessCalculator.effectiveness("MID", "ATT"));
    }

    // ========== WINGER row (carrilero flexibility) ==========

    @Test
    @DisplayName("WINGER in GK slot → 0.0 (invalid)")
    void wingerInGK() {
        assertEquals(0.0, PositionEffectivenessCalculator.effectiveness("WINGER", "GK"));
    }

    @Test
    @DisplayName("WINGER in DEF slot → 0.8 (limited — wing back in defense)")
    void wingerInDef() {
        assertEquals(0.8, PositionEffectivenessCalculator.effectiveness("WINGER", "DEF"));
    }

    @Test
    @DisplayName("WINGER in MID slot → 0.95 (carrilero flexibility — LWB/RWB spirit)")
    void wingerInMid() {
        // KEY assertion: 0.95 not 1.0. WINGER in MID is the canonical
        // carrilero flexibility (LWB/RWB average per the task spec).
        assertEquals(0.95, PositionEffectivenessCalculator.effectiveness("WINGER", "MID"));
    }

    @Test
    @DisplayName("WINGER in ATT slot → 0.9 (LW/RW spirit)")
    void wingerInAtt() {
        assertEquals(0.9, PositionEffectivenessCalculator.effectiveness("WINGER", "ATT"));
    }

    // ========== ATT row ==========

    @Test
    @DisplayName("ATT in GK slot → 0.0 (invalid)")
    void attInGK() {
        assertEquals(0.0, PositionEffectivenessCalculator.effectiveness("ATT", "GK"));
    }

    @Test
    @DisplayName("ATT in DEF slot → 0.3 (severe penalty — no defensive instincts)")
    void attInDef() {
        assertEquals(0.3, PositionEffectivenessCalculator.effectiveness("ATT", "DEF"));
    }

    @Test
    @DisplayName("ATT in MID slot → 0.7 (false-9 / shadow striker)")
    void attInMid() {
        assertEquals(0.7, PositionEffectivenessCalculator.effectiveness("ATT", "MID"));
    }

    @Test
    @DisplayName("ATT in ATT slot → 1.0 (perfect match)")
    void attInAtt() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("ATT", "ATT"));
    }

    // ========== Backward compat ==========

    @Test
    @DisplayName("null naturalPosition → 1.0 (backyard compat)")
    void nullNatural() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness(null, "MID"));
    }

    @Test
    @DisplayName("null slotCategory → 1.0 (backyard compat)")
    void nullSlot() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("DEF", null));
    }

    @Test
    @DisplayName("both null → 1.0 (backyard compat)")
    void bothNull() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness(null, null));
    }

    @Test
    @DisplayName("Blank inputs → 1.0 (backyard compat)")
    void blankInputs() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("", "MID"));
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("DEF", ""));
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("   ", "MID"));
    }

    @Test
    @DisplayName("Unknown naturalCategory → 1.0 (graceful degradation)")
    void unknownNatural() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("FUTURE_POS", "MID"));
    }

    @Test
    @DisplayName("Unknown slotCategory → 1.0 (graceful degradation)")
    void unknownSlot() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("DEF", "FUTURE_CAT"));
    }

    // ========== V25D51 (Sprint C13): 3-cat → 5-cat mapper ==========

    @Test
    @DisplayName("mapper: GK passes through to GK")
    void mapper_gk() {
        assertEquals("GK", PositionEffectivenessCalculator.toFiveCategory("GK"));
    }

    @Test
    @DisplayName("mapper: CB/LB/RB/LWB/RWB collapse to DEF")
    void mapper_defVariants() {
        assertEquals("DEF", PositionEffectivenessCalculator.toFiveCategory("CB"));
        assertEquals("DEF", PositionEffectivenessCalculator.toFiveCategory("LB"));
        assertEquals("DEF", PositionEffectivenessCalculator.toFiveCategory("RB"));
        assertEquals("DEF", PositionEffectivenessCalculator.toFiveCategory("LWB"));
        assertEquals("DEF", PositionEffectivenessCalculator.toFiveCategory("RWB"));
    }

    @Test
    @DisplayName("mapper: CDM/CM/CAM/LM/RM collapse to MID")
    void mapper_midVariants() {
        assertEquals("MID", PositionEffectivenessCalculator.toFiveCategory("CDM"));
        assertEquals("MID", PositionEffectivenessCalculator.toFiveCategory("CM"));
        assertEquals("MID", PositionEffectivenessCalculator.toFiveCategory("CAM"));
        assertEquals("MID", PositionEffectivenessCalculator.toFiveCategory("LM"));
        assertEquals("MID", PositionEffectivenessCalculator.toFiveCategory("RM"));
    }

    @Test
    @DisplayName("mapper: LW/RW collapse to WINGER")
    void mapper_wingerVariants() {
        assertEquals("WINGER", PositionEffectivenessCalculator.toFiveCategory("LW"));
        assertEquals("WINGER", PositionEffectivenessCalculator.toFiveCategory("RW"));
    }

    @Test
    @DisplayName("mapper: CF/ST collapse to ATT")
    void mapper_attVariants() {
        assertEquals("ATT", PositionEffectivenessCalculator.toFiveCategory("CF"));
        assertEquals("ATT", PositionEffectivenessCalculator.toFiveCategory("ST"));
    }

    @Test
    @DisplayName("mapper: 5-cat names pass through unchanged")
    void mapper_fiveCatPassThrough() {
        assertEquals("GK", PositionEffectivenessCalculator.toFiveCategory("GK"));
        assertEquals("DEF", PositionEffectivenessCalculator.toFiveCategory("DEF"));
        assertEquals("MID", PositionEffectivenessCalculator.toFiveCategory("MID"));
        assertEquals("WINGER", PositionEffectivenessCalculator.toFiveCategory("WINGER"));
        assertEquals("ATT", PositionEffectivenessCalculator.toFiveCategory("ATT"));
    }

    @Test
    @DisplayName("mapper: unknown / null / blank return as-is (no penalty path)")
    void mapper_unknownInputs() {
        assertEquals("FUTURE_POS", PositionEffectivenessCalculator.toFiveCategory("FUTURE_POS"));
        assertEquals("", PositionEffectivenessCalculator.toFiveCategory(""));
        assertEquals("   ", PositionEffectivenessCalculator.toFiveCategory("   "));
        assertEquals(null, PositionEffectivenessCalculator.toFiveCategory(null));
    }

    // ========== V25D51 (Sprint C13): integration — 3-cat inputs through effectiveness() ==========

    @Test
    @DisplayName("C13 integration: LW (3-cat) in MID slot → 0.95 (carrilero flexibility)")
    void c13_lwInMid() {
        // V25D51 task spec evidence line 1: Rodrygo (LW) en S15-1 (MID) → 0.95.
        assertEquals(0.95, PositionEffectivenessCalculator.effectiveness("LW", "MID"));
    }

    @Test
    @DisplayName("C13 integration: CM (3-cat) in DEF slot → 0.85 (CDM-like)")
    void c13_cmInDef() {
        // V25D51 task spec evidence line 2: Carvajal/Vazquez/Rudiger (CM) en
        // S22-1/S22-2/S23-2 (DEF) → 0.85.
        assertEquals(0.85, PositionEffectivenessCalculator.effectiveness("CM", "DEF"));
    }

    @Test
    @DisplayName("C13 integration: ST (3-cat) in MID slot → 0.7 (false-9 / shadow striker)")
    void c13_stInMid() {
        assertEquals(0.7, PositionEffectivenessCalculator.effectiveness("ST", "MID"));
    }

    @Test
    @DisplayName("C13 integration: CB (3-cat) in MID slot → 0.8 (CB penalty)")
    void c13_cbInMid() {
        assertEquals(0.8, PositionEffectivenessCalculator.effectiveness("CB", "MID"));
    }

    @Test
    @DisplayName("C13 integration: GK (3-cat pass-through) in GK slot → 1.0")
    void c13_gkInGk() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("GK", "GK"));
    }

    @Test
    @DisplayName("C13 integration: ST (3-cat) in ATT slot → 1.0 (perfect match)")
    void c13_stInAtt() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("ST", "ATT"));
    }

    @Test
    @DisplayName("C13 integration: LWB (3-cat, mapped to DEF) in MID slot → 0.8 (CB penalty)")
    void c13_lwbInMid() {
        // LWB is a wing-back (defensive) → maps to DEF → DEF in MID = 0.8.
        // The 0.95 "carrilero flexibility" applies to LW/RW (WINGER→MID), not LWB/RWB.
        assertEquals(0.8, PositionEffectivenessCalculator.effectiveness("LWB", "MID"));
    }

    @Test
    @DisplayName("C13 integration: RWB (3-cat, mapped to DEF) in ATT slot → 0.4 (severe penalty)")
    void c13_rwbInAtt() {
        // RWB is a wing-back (defensive) → maps to DEF → DEF in ATT = 0.4.
        assertEquals(0.4, PositionEffectivenessCalculator.effectiveness("RWB", "ATT"));
    }

    @Test
    @DisplayName("C13 integration: LB (3-cat, mapped to DEF) in DEF slot → 1.0 (perfect)")
    void c13_lbInDef() {
        // LB is a defensive position → maps to DEF → DEF in DEF = 1.0.
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("LB", "DEF"));
    }

    @Test
    @DisplayName("C13 integration: RW (3-cat, mapped to WINGER) in ATT slot → 0.9")
    void c13_rwInAtt() {
        // RW is an attacking winger → maps to WINGER → WINGER in ATT = 0.9.
        assertEquals(0.9, PositionEffectivenessCalculator.effectiveness("RW", "ATT"));
    }

    @Test
    @DisplayName("C13 integration: CF (3-cat) in DEF slot → 0.3 (severe penalty)")
    void c13_cfInDef() {
        assertEquals(0.3, PositionEffectivenessCalculator.effectiveness("CF", "DEF"));
    }

    @Test
    @DisplayName("C13 integration: CAM (3-cat) in MID slot → 1.0 (perfect)")
    void c13_camInMid() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("CAM", "MID"));
    }

    @Test
    @DisplayName("C13 integration: CDM (3-cat) in MID slot → 1.0 (perfect)")
    void c13_cdmInMid() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness("CDM", "MID"));
    }

    // ========== V25D51 (Sprint C13): backward compat — 5-cat inputs unchanged ==========

    @Test
    @DisplayName("C13 backward compat: WINGER (5-cat) in MID slot → 0.95 (unchanged)")
    void c13_backcompatWingerMid() {
        // 5-cat passthrough still works (V25D47 contract preserved).
        assertEquals(0.95, PositionEffectivenessCalculator.effectiveness("WINGER", "MID"));
    }

    @Test
    @DisplayName("C13 backward compat: DEF (5-cat) in MID slot → 0.8 (unchanged)")
    void c13_backcompatDefMid() {
        assertEquals(0.8, PositionEffectivenessCalculator.effectiveness("DEF", "MID"));
    }

    @Test
    @DisplayName("C13 backward compat: MID (5-cat) in ATT slot → 0.85 (unchanged)")
    void c13_backcompatMidAtt() {
        assertEquals(0.85, PositionEffectivenessCalculator.effectiveness("MID", "ATT"));
    }

    @Test
    @DisplayName("C13 backward compat: null naturalPosition still → 1.0")
    void c13_backcompatNullNatural() {
        assertEquals(1.0, PositionEffectivenessCalculator.effectiveness(null, "MID"));
    }
}