package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class TestHarnessCommonSupport {

    private TestHarnessCommonSupport() {
    }

    static String resolveControlledTeamId(CareerSave career, MatchFixture fixture, String controlledTeamSide) {
        String side = controlledTeamSide == null || controlledTeamSide.isBlank()
            ? "USER"
            : controlledTeamSide.trim().toUpperCase(Locale.ROOT);
        return switch (side) {
            case "HOME" -> fixture.getHomeTeamId();
            case "AWAY" -> fixture.getAwayTeamId();
            case "USER" -> {
                String userTeamId = career.getUserSessionTeamId();
                boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
                boolean userIsAway = fixture.getAwayTeamId().equals(userTeamId);
                if (!userIsHome && !userIsAway) {
                    throw new IllegalArgumentException(
                        "Controlled USER side requires a match involving the user team: " + userTeamId);
                }
                yield userTeamId;
            }
            default -> throw new IllegalArgumentException(
                "controlledTeamSide must be USER, HOME or AWAY");
        };
    }

    static Optional<SessionPlayer> findPlayer(List<SessionPlayer> players, String playerId) {
        if (players == null || playerId == null) {
            return Optional.empty();
        }
        return players.stream()
            .filter(player -> player != null && playerId.equals(player.getSessionPlayerId()))
            .findFirst();
    }

    static Integer playerOverall(SessionPlayer player) {
        if (player == null) {
            return null;
        }
        return Math.round((
            safeInt(player.getAttack())
                + safeInt(player.getDefense())
                + safeInt(player.getTechnique())
                + safeInt(player.getSpeed())
                + safeInt(player.getStamina())
                + safeInt(player.getMentality())) / 6.0f);
    }

    static String safeName(SessionPlayer player) {
        if (player == null) {
            return "";
        }
        return player.getName() != null ? player.getName() : player.getSessionPlayerId();
    }

    static String currentFormation(CareerSave career, String teamId, SessionTeam team) {
        if (career.getTeamStarting11Formation() != null
            && career.getTeamStarting11Formation().containsKey(teamId)
            && career.getTeamStarting11Formation().get(teamId) != null
            && !career.getTeamStarting11Formation().get(teamId).isBlank()) {
            return career.getTeamStarting11Formation().get(teamId);
        }
        return team != null && team.getFormation() != null
            ? team.getFormation()
            : "4-4-2";
    }

    static int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
