package com.footballmanager.domain.model.valueobject;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D47 (Sprint C11a): unit tests for {@link FormationInferer}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Infer algorithm: 4-4-2, 3-5-2, 5-3-2, 4-3-3 (canonical formations).
 *   <li>Backward compat: null / empty slots → default "4-4-2".
 *   <li>Malformed inputs: wrong size, no GK, garbage subdivisionIds.
 *   <li>categoryFor: GK-1, S##-# parsing, edge sectors (1, 6, 7, 18, 19, 27), invalid.
 * </ul>
 */
@DisplayName("FormationInferer — V25D47 formation inference from subdivision slots")
class FormationInfererTest {

    private LineupSlotDTO slot(String playerId, String subdivisionId) {
        return new LineupSlotDTO(playerId, subdivisionId);
    }

    /** Build an 11-slot lineup with the given DEF/MID/ATT counts plus the GK slot. */
    private List<LineupSlotDTO> lineupWith(int def, int mid, int att) {
        if (def + mid + att != 10) {
            throw new IllegalArgumentException("DEF+MID+ATT must equal 10 (got " + (def + mid + att) + ")");
        }
        List<LineupSlotDTO> slots = new ArrayList<>(11);

        // 1 GK
        slots.add(slot("p-gk", "GK-1"));

        // DEF: sectors 19-27 (bottom of field, rows 6-8)
        // Pick leftmost sub-slot per sector to keep counts predictable.
        // 5 entries needed to support the 5-3-2 formation's 5-defender case.
        int defSectors[] = {27, 24, 21, 20, 19}; // rows 8/7/6/6/6
        for (int i = 0; i < def; i++) {
            slots.add(slot("p-def-" + i, "S" + String.format("%02d", defSectors[i]) + "-1"));
        }

        // MID: sectors 7-18 (middle, rows 2-5)
        // 5 entries to support the 3-5-2 formation's 5-midfielder case.
        int midSectors[] = {18, 15, 12, 9, 7}; // rows 5/4/3/2/2
        for (int i = 0; i < mid; i++) {
            slots.add(slot("p-mid-" + i, "S" + String.format("%02d", midSectors[i]) + "-1"));
        }

        // ATT: sectors 1-6 (top of field, rows 0-1)
        int attSectors[] = {6, 3, 1}; // 3 sectors at rows 1/0/0
        for (int i = 0; i < att; i++) {
            slots.add(slot("p-att-" + i, "S" + String.format("%02d", attSectors[i]) + "-1"));
        }

        return slots;
    }

    // ========== Infer algorithm ==========

    @Test
    @DisplayName("infer: 4-4-2 lineup (1 GK + 4 DEF + 4 MID + 2 ATT) → '4-4-2'")
    void infer_442() {
        assertEquals("4-4-2", FormationInferer.infer(lineupWith(4, 4, 2)));
    }

    @Test
    @DisplayName("infer: 3-5-2 lineup (1 GK + 3 DEF + 5 MID + 2 ATT) → '3-5-2'")
    void infer_352() {
        assertEquals("3-5-2", FormationInferer.infer(lineupWith(3, 5, 2)));
    }

    @Test
    @DisplayName("infer: 5-3-2 lineup (1 GK + 5 DEF + 3 MID + 2 ATT) → '5-3-2'")
    void infer_532() {
        assertEquals("5-3-2", FormationInferer.infer(lineupWith(5, 3, 2)));
    }

    @Test
    @DisplayName("infer: 4-3-3 lineup (1 GK + 4 DEF + 3 MID + 3 ATT) → '4-3-3'")
    void infer_433() {
        assertEquals("4-3-3", FormationInferer.infer(lineupWith(4, 3, 3)));
    }

    // ========== Backward compat ==========

    @Test
    @DisplayName("infer: null slots → '4-4-2' default (backward compat)")
    void infer_nullSlots() {
        assertEquals("4-4-2", FormationInferer.infer(null));
    }

    @Test
    @DisplayName("infer: empty slots → '4-4-2' default (backyard compat)")
    void infer_emptySlots() {
        assertEquals("4-4-2", FormationInferer.infer(List.of()));
    }

    @Test
    @DisplayName("infer: 10 slots (missing player) → '4-4-2' graceful degradation")
    void infer_shortSlots() {
        List<LineupSlotDTO> ten = lineupWith(4, 4, 2).subList(0, 10);
        assertEquals("4-4-2", FormationInferer.infer(ten));
    }

    @Test
    @DisplayName("infer: 12 slots (too many) → '4-4-2' graceful degradation")
    void infer_tooManySlots() {
        List<LineupSlotDTO> twelve = new ArrayList<>(lineupWith(4, 4, 2));
        twelve.add(slot("p-extra", "S01-1"));
        assertEquals("4-4-2", FormationInferer.infer(twelve));
    }

    @Test
    @DisplayName("infer: 0 GK (broken lineup) → '4-4-2' graceful degradation")
    void infer_noGk() {
        // 11 outfield slots only, no GK-1
        List<LineupSlotDTO> noGk = lineupWith(4, 4, 2);
        // Replace the GK slot with an outfield slot (zone ATT)
        noGk.set(0, slot("p-no-gk", "S01-1"));
        assertEquals("4-4-2", FormationInferer.infer(noGk));
    }

    @Test
    @DisplayName("infer: garbage subdivisionId → skipped, may yield default")
    void infer_garbageSubdivisionId() {
        List<LineupSlotDTO> garbage = new ArrayList<>();
        garbage.add(slot("p-gk", "GARBAGE-NOT-SECTOR"));
        garbage.add(slot("p1", "XX-1"));
        // Total after filter = 0, no GK → default
        assertEquals("4-4-2", FormationInferer.infer(garbage));
    }

    // ========== categoryFor mapping ==========

    @Nested
    @DisplayName("categoryFor: subdivisionId → GK/DEF/MID/ATT")
    class CategoryForTests {

        @Test
        @DisplayName("GK-1 → 'GK' (special GK slot)")
        void categoryFor_gk() {
            assertEquals("GK", FormationInferer.categoryFor("GK-1"));
        }

        @Test
        @DisplayName("Sector 1 (top row) → 'ATT'")
        void categoryFor_sector1() {
            assertEquals("ATT", FormationInferer.categoryFor("S01-1"));
            assertEquals("ATT", FormationInferer.categoryFor("S01-2"));
            assertEquals("ATT", FormationInferer.categoryFor("S01-3"));
        }

        @Test
        @DisplayName("Sector 6 (row 1 → ATT) → 'ATT'")
        void categoryFor_sector6() {
            assertEquals("ATT", FormationInferer.categoryFor("S06-1"));
        }

        @Test
        @DisplayName("Sector 7 (row 2 → MID) → 'MID'")
        void categoryFor_sector7() {
            assertEquals("MID", FormationInferer.categoryFor("S07-1"));
        }

        @Test
        @DisplayName("Sector 18 (row 5 → MID) → 'MID'")
        void categoryFor_sector18() {
            assertEquals("MID", FormationInferer.categoryFor("S18-3"));
        }

        @Test
        @DisplayName("Sector 19 (row 6 → DEF) → 'DEF'")
        void categoryFor_sector19() {
            assertEquals("DEF", FormationInferer.categoryFor("S19-1"));
        }

        @Test
        @DisplayName("Sector 27 (row 8 → DEF) → 'DEF'")
        void categoryFor_sector27() {
            assertEquals("DEF", FormationInferer.categoryFor("S27-3"));
        }

        @Test
        @DisplayName("null subdivisionId → null")
        void categoryFor_null() {
            assertNull(FormationInferer.categoryFor(null));
        }

        @Test
        @DisplayName("Blank subdivisionId → null")
        void categoryFor_blank() {
            assertNull(FormationInferer.categoryFor(""));
            assertNull(FormationInferer.categoryFor("   "));
        }

        @Test
        @DisplayName("Malformed subdivisionId (no dash) → null")
        void categoryFor_noDash() {
            assertNull(FormationInferer.categoryFor("S01"));
            assertNull(FormationInferer.categoryFor("INVALID"));
        }

        @Test
        @DisplayName("Out-of-range sector (0 or 28) → null")
        void categoryFor_outOfRange() {
            assertNull(FormationInferer.categoryFor("S00-1"));
            assertNull(FormationInferer.categoryFor("S28-1"));
        }
    }
}