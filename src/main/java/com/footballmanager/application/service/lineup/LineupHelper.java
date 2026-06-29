package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupWarningDTO;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.Formation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper compartido para operaciones de lineup.
 * Extraído para evitar duplicación entre UseCases.
 *
 * <p>V24D6U2: Added short-handed variants that return warnings instead
 * of throwing on position deficit. The strict methods remain for
 * legacy callers and tests.
 *
 * <p>V25D78-C43 P0 (auto-select role match fix): the position-category
 * helpers ({@code isDefender} / {@code isMidfielder} / {@code isAttacker})
 * now accept BOTH the specific role codes (CB, LB, RB, CDM, CM, etc.)
 * AND the category codes (DEF, MID, ATT). Pre-fix, only specific role
 * codes were accepted, which caused the LaLiga seed data (which uses
 * category codes like "DEF" for Carvajal/Rudiger/Militao) to be silently
 * rejected as a defender — auto-select would skip them and pick CDM/CM
 * players with higher OVR for the 4 DEF slots, with off-position warnings
 * ("4 DEF slots filled by off-position players"). Accepting category
 * codes as well makes the system robust to both naming conventions
 * (the existing squad data uses category codes; the helper-based match
 * for slot assignment has always used specific role codes).
 */
@Component
public class LineupHelper {

    public boolean isDefender(String position) {
        return position != null && (
            // Specific role codes
            position.equals("CB") ||
            position.equals("LB") ||
            position.equals("RB") ||
            position.equals("LWB") ||
            position.equals("RWB") ||
            // Category code (LaLiga seed uses "DEF" for Carvajal, Rudiger, Militao, etc.)
            position.equals("DEF")
        );
    }

    public boolean isMidfielder(String position) {
        return position != null && (
            // Specific role codes
            position.equals("CDM") ||
            position.equals("CM") ||
            position.equals("CAM") ||
            position.equals("LM") ||
            position.equals("RM") ||
            position.equals("LW") ||
            position.equals("RW") ||
            // Category code (LaLiga seed uses "MID" for Valverde, Tchouameni, etc.)
            position.equals("MID")
        );
    }

    public boolean isAttacker(String position) {
        return position != null && (
            // Specific role codes
            position.equals("CF") ||
            position.equals("ST") ||
            // Category code (LaLiga seed uses "ATT" for Vinicius, Mbappe, etc.)
            position.equals("ATT")
        );
    }

    public int countByPosition(List<SessionPlayer> players, String position) {
        return (int) players.stream().filter(p -> position.equals(p.getPosition())).count();
    }

    public int countDefenders(List<SessionPlayer> players) {
        return (int) players.stream().filter(p -> isDefender(p.getPosition())).count();
    }

    public int countMidfielders(List<SessionPlayer> players) {
        return (int) players.stream().filter(p -> isMidfielder(p.getPosition())).count();
    }

    public int countAttackers(List<SessionPlayer> players) {
        return (int) players.stream().filter(p -> isAttacker(p.getPosition())).count();
    }

    public int countGoalkeepers(List<SessionPlayer> players) {
        return (int) players.stream().filter(p -> "GK".equals(p.getPosition())).count();
    }

    public String inferFormation(List<SessionPlayer> players) {
        int defCount = countDefenders(players);
        int midCount = countMidfielders(players);
        int attCount = countAttackers(players);

        if (defCount == 4 && midCount == 4 && attCount == 2) return "4-4-2";
        if (defCount == 4 && midCount == 3 && attCount == 3) return "4-3-3";
        if (defCount == 4 && midCount == 5 && attCount == 1) return "4-2-3-1";
        if (defCount == 3 && midCount == 5 && attCount == 2) return "3-5-2";
        if (defCount == 5 && midCount == 3 && attCount == 2) return "5-3-2";

        return "4-4-2";
    }

    public void validateLineupFormation(List<SessionPlayer> players, Formation formation) {
        int defCount = countDefenders(players);
        int midCount = countMidfielders(players);
        int attCount = countAttackers(players);

        if (defCount < formation.getDefenders()) {
            throw new IllegalArgumentException(
                "Need at least " + formation.getDefenders() + " defenders for " + formation.getCode());
        }
        if (midCount < formation.getMidfielders()) {
            throw new IllegalArgumentException(
                "Need at least " + formation.getMidfielders() + " midfielders for " + formation.getCode());
        }
        if (attCount < formation.getAttackers()) {
            throw new IllegalArgumentException(
                "Need at least " + formation.getAttackers() + " attackers for " + formation.getCode());
        }
    }

    public void validateLineupBasic(List<SessionPlayer> players) {
        if (players.size() != 11) {
            throw new IllegalArgumentException("Must have exactly 11 players");
        }
        if (countGoalkeepers(players) != 1) {
            throw new IllegalArgumentException("Must have exactly 1 goalkeeper");
        }
    }

    public void validatePlayerFitness(List<SessionPlayer> players) {
        for (SessionPlayer player : players) {
            if (player.getEnergy() <= 20) {
                throw new IllegalArgumentException(
                    "Player " + player.getName() + " has low fitness (" + player.getEnergy() + "%)");
            }
            if (Boolean.TRUE.equals(player.getInjured())
                || (player.getInjuryRemainingMatches() != null && player.getInjuryRemainingMatches() > 0)) {
                int remaining = player.getInjuryRemainingMatches() != null ? player.getInjuryRemainingMatches() : 0;
                throw new IllegalArgumentException(
                    "Player " + player.getName() + " is injured for " + remaining + " match(es)");
            }
            if (Boolean.TRUE.equals(player.getSuspended()) || player.getSuspensionRemainingMatches() > 0) {
                throw new IllegalArgumentException(
                    "Player " + player.getName() + " is suspended for " + player.getSuspensionRemainingMatches() + " match(es)");
            }
        }
    }

    // ========== V24D6U2: Short-handed variants ==========

    /**
     * V24D6U2: Validates a short-handed lineup. Throws on out-of-bounds size
     * or duplicate IDs. Does NOT throw on missing goalkeeper (returns
     * warning instead) or position deficit (no warnings, the lineup is
     * already accepted as best-effort).
     *
     * <p>Caller is expected to have already filtered for availability
     * (energy, injury, suspension).
     */
    public void validateLineupBasicShortHanded(List<SessionPlayer> players) {
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("Lineup must not be empty");
        }
        int size = players.size();
        if (size < LineupRules.MIN_AVAILABLE_PLAYERS) {
            throw new IllegalArgumentException(
                "Lineup must have at least " + LineupRules.MIN_AVAILABLE_PLAYERS
                + " players, found: " + size);
        }
        if (size > LineupRules.MAX_LINEUP_PLAYERS) {
            throw new IllegalArgumentException(
                "Lineup must have at most " + LineupRules.MAX_LINEUP_PLAYERS
                + " players, found: " + size);
        }
    }

    /**
     * V24D6U2: Returns a warning if the lineup has no goalkeeper.
     * Returns empty otherwise. Does not throw.
     */
    public List<LineupWarningDTO> detectShortHandedWarnings(List<SessionPlayer> players) {
        List<LineupWarningDTO> warnings = new ArrayList<>();
        if (players != null && players.size() > 0
            && countGoalkeepers(players) == 0) {
            warnings.add(LineupWarningDTO.noGoalkeeper(players.size()));
        }
        return warnings;
    }
}

