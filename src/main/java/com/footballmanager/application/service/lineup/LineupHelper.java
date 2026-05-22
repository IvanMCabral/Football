package com.footballmanager.application.service.lineup;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.Formation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Helper compartido para operaciones de lineup.
 * Extraído para evitar duplicación entre UseCases.
 */
@Component
public class LineupHelper {

    public boolean isDefender(String position) {
        return position != null && (
            position.equals("CB") ||
            position.equals("LB") ||
            position.equals("RB") ||
            position.equals("LWB") ||
            position.equals("RWB")
        );
    }

    public boolean isMidfielder(String position) {
        return position != null && (
            position.equals("CDM") ||
            position.equals("CM") ||
            position.equals("CAM") ||
            position.equals("LM") ||
            position.equals("RM") ||
            position.equals("LW") ||
            position.equals("RW")
        );
    }

    public boolean isAttacker(String position) {
        return position != null && (
            position.equals("CF") ||
            position.equals("ST")
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
}
