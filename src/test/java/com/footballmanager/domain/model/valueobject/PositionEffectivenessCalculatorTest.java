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
}