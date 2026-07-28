package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TestHarnessContextMutationSupport {

    private TestHarnessContextMutationSupport() {
    }

    static V24MatchContext buildInitialSwapContext(
            V24MatchContext context,
            String userTeamId,
            String starterPlayerId,
            String benchPlayerId) {

        boolean userIsHome = context.homeTeamId().equals(userTeamId);
        boolean userIsAway = context.awayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException("userTeamId does not belong to context: " + userTeamId);
        }

        List<SessionPlayer> userStarters = new ArrayList<>(
            userIsHome ? context.homeStartingPlayers() : context.awayStartingPlayers());
        List<SessionPlayer> userBench = new ArrayList<>(
            userIsHome ? context.homeBenchPlayers() : context.awayBenchPlayers());

        SessionPlayer starter = TestHarnessCommonSupport.findPlayer(userStarters, starterPlayerId)
            .orElseThrow(() -> new IllegalArgumentException(
                "starterPlayerId '" + starterPlayerId + "' not in user starting XI"));
        SessionPlayer bench = TestHarnessCommonSupport.findPlayer(userBench, benchPlayerId)
            .orElseThrow(() -> new IllegalArgumentException(
                "benchPlayerId '" + benchPlayerId + "' not on user bench"));

        swapStarterAndBench(starterPlayerId, benchPlayerId, userStarters, userBench, starter, bench);
        Map<String, LineupSlot> homeSlots = context.homeSlotsByPlayerId();
        Map<String, LineupSlot> awaySlots = context.awaySlotsByPlayerId();
        Map<String, LineupSlot> userSlots = new LinkedHashMap<>(userIsHome ? homeSlots : awaySlots);
        LineupSlot previousSlot = userSlots.remove(starterPlayerId);
        if (previousSlot != null) {
            userSlots.put(benchPlayerId, new LineupSlot(
                benchPlayerId,
                previousSlot.subdivisionId(),
                previousSlot.customXPercent(),
                previousSlot.customYPercent()));
        }

        return new V24MatchContext(
            context.matchId(),
            context.homeTeamId(),
            context.awayTeamId(),
            context.homeTeam(),
            context.awayTeam(),
            userIsHome ? userStarters : context.homeStartingPlayers(),
            userIsHome ? context.awayStartingPlayers() : userStarters,
            userIsHome ? userBench : context.homeBenchPlayers(),
            userIsHome ? context.awayBenchPlayers() : userBench,
            context.homeFormation(),
            context.awayFormation(),
            context.homeStyle(),
            context.awayStyle(),
            context.manualSubstitutions(),
            userIsHome ? userSlots : homeSlots,
            userIsHome ? awaySlots : userSlots);
    }

    static V24MatchContext buildMovedPositionContext(
            V24MatchContext context,
            String userTeamId,
            String playerId,
            String slotId,
            double targetXPercent,
            double targetYPercent) {
        boolean userIsHome = context.homeTeamId().equals(userTeamId);
        boolean userIsAway = context.awayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException("userTeamId does not belong to context: " + userTeamId);
        }
        Map<String, LineupSlot> homeSlots = context.homeSlotsByPlayerId();
        Map<String, LineupSlot> awaySlots = context.awaySlotsByPlayerId();
        Map<String, LineupSlot> userSlots = new LinkedHashMap<>(userIsHome ? homeSlots : awaySlots);
        LineupSlot previous = userSlots.get(playerId);
        userSlots.put(playerId, new LineupSlot(
            playerId,
            previous != null && previous.subdivisionId() != null ? previous.subdivisionId() : slotId,
            targetXPercent,
            targetYPercent));
        return new V24MatchContext(
            context.matchId(),
            context.homeTeamId(),
            context.awayTeamId(),
            context.homeTeam(),
            context.awayTeam(),
            context.homeStartingPlayers(),
            context.awayStartingPlayers(),
            context.homeBenchPlayers(),
            context.awayBenchPlayers(),
            context.homeFormation(),
            context.awayFormation(),
            context.homeStyle(),
            context.awayStyle(),
            context.manualSubstitutions(),
            userIsHome ? userSlots : homeSlots,
            userIsHome ? awaySlots : userSlots);
    }

    static V24MatchContext buildRoleOverrideContext(
            V24MatchContext context,
            String userTeamId,
            String playerId,
            String naturalPosition) {
        boolean userIsHome = context.homeTeamId().equals(userTeamId);
        boolean userIsAway = context.awayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException("userTeamId does not belong to context: " + userTeamId);
        }
        List<SessionPlayer> starters = new ArrayList<>(
            userIsHome ? context.homeStartingPlayers() : context.awayStartingPlayers());
        boolean replaced = false;
        for (int i = 0; i < starters.size(); i++) {
            SessionPlayer current = starters.get(i);
            if (current != null && playerId.equals(current.getSessionPlayerId())) {
                starters.set(i, roleOverrideClone(current, naturalPosition));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException("playerId '" + playerId + "' not in controlled starting XI");
        }
        return new V24MatchContext(
            context.matchId(),
            context.homeTeamId(),
            context.awayTeamId(),
            context.homeTeam(),
            context.awayTeam(),
            userIsHome ? starters : context.homeStartingPlayers(),
            userIsHome ? context.awayStartingPlayers() : starters,
            context.homeBenchPlayers(),
            context.awayBenchPlayers(),
            context.homeFormation(),
            context.awayFormation(),
            context.homeStyle(),
            context.awayStyle(),
            context.manualSubstitutions(),
            context.homeSlotsByPlayerId(),
            context.awaySlotsByPlayerId());
    }

    private static void swapStarterAndBench(
            String starterPlayerId,
            String benchPlayerId,
            List<SessionPlayer> starters,
            List<SessionPlayer> benchPlayers,
            SessionPlayer starter,
            SessionPlayer bench) {
        for (int i = 0; i < starters.size(); i++) {
            SessionPlayer current = starters.get(i);
            if (current != null && starterPlayerId.equals(current.getSessionPlayerId())) {
                starters.set(i, bench);
                break;
            }
        }
        for (int i = 0; i < benchPlayers.size(); i++) {
            SessionPlayer current = benchPlayers.get(i);
            if (current != null && benchPlayerId.equals(current.getSessionPlayerId())) {
                benchPlayers.set(i, starter);
                break;
            }
        }
    }

    static SessionPlayer roleOverrideClone(SessionPlayer source, String naturalPosition) {
        SessionPlayer clone = SessionPlayer.custom(
            source.getName(),
            source.getAge(),
            naturalPosition,
            source.getAttack(),
            source.getDefense(),
            source.getTechnique(),
            source.getSpeed(),
            source.getStamina(),
            source.getMentality(),
            source.getMarketValue());
        clone.setSessionPlayerId(source.getSessionPlayerId());
        clone.setBasePlayerId(source.getBasePlayerId());
        clone.setWorldPlayerId(source.getWorldPlayerId());
        clone.setEnergy(source.getEnergy());
        clone.setForm(source.getForm());
        clone.setInjured(source.getInjured());
        clone.setInjuryType(source.getInjuryType());
        clone.setInjuryRemainingMatches(source.getInjuryRemainingMatches());
        clone.setMatchesPlayedInRow(source.getMatchesPlayedInRow());
        clone.setYellowCards(source.getYellowCards());
        clone.setRedCards(source.getRedCards());
        clone.setSuspended(source.getSuspended());
        clone.setSuspensionRemainingMatches(source.getSuspensionRemainingMatches());
        clone.setOrigin(source.getOrigin());
        clone.setHeightCm(source.getHeightCm());
        for (Map.Entry<PlayerSkill, Integer> entry : source.getSkillLevels().entrySet()) {
            clone.setSkillLevel(entry.getKey(), entry.getValue());
        }
        return clone;
    }
}
