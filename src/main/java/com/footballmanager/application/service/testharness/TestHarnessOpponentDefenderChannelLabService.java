package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.testharness.LabMutationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
@Slf4j
class TestHarnessOpponentDefenderChannelLabService {

    private final TestHarnessDefenderChannelSupport support;

    public Mono<LabMutationResult> prepareOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-left-defender:" + matchId,
            "prepare-opponent-weak-left-defender-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.LEFT,
            1,
            45, 25, 45, 45, 55, 25,
            "Prepared opponent weak left defender lab: selected rival left DEF made vulnerable",
            "RIGHT_FLANK/WIDE_PLAY should gain relative xG when the opponent left defender is vulnerable");
    }

    public Mono<LabMutationResult> restoreOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-left-defender:" + matchId,
            "restore-opponent-weak-left-defender-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.LEFT,
            1,
            76, 76, 76, 76, 76, 76,
            "Restored opponent weak left defender lab player",
            "Opponent left defender restored; attacking channel advantage should normalize");
    }

    public Mono<LabMutationResult> prepareOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-right-defender:" + matchId,
            "prepare-opponent-weak-right-defender-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.RIGHT,
            1,
            45, 25, 45, 45, 55, 25,
            "Prepared opponent weak right defender lab: selected rival right DEF made vulnerable",
            "LEFT_FLANK/WIDE_PLAY should gain relative xG when the opponent right defender is vulnerable");
    }

    public Mono<LabMutationResult> restoreOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-right-defender:" + matchId,
            "restore-opponent-weak-right-defender-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.RIGHT,
            1,
            76, 76, 76, 76, 76, 76,
            "Restored opponent weak right defender lab player",
            "Opponent right defender restored; attacking channel advantage should normalize");
    }

    public Mono<LabMutationResult> prepareOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-center-backs:" + matchId,
            "prepare-opponent-weak-center-backs-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.CENTER,
            2,
            45, 25, 45, 45, 55, 25,
            "Prepared opponent weak center backs lab: selected rival central DEF starters made vulnerable",
            "CENTRAL_PLAY should gain relative xG/xG-diff versus WIDE_PLAY when opponent center backs are vulnerable");
    }

    public Mono<LabMutationResult> restoreOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-center-backs:" + matchId,
            "restore-opponent-weak-center-backs-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.CENTER,
            2,
            76, 76, 76, 76, 76, 76,
            "Restored opponent weak center backs lab players",
            "CENTRAL_PLAY should return to normal after restoring opponent center backs");
    }


    private Mono<LabMutationResult> mutateOpponentWeakDefenderChannelLab(
            UUID userId,
            String matchId,
            String snapshotKey,
            String labKey,
            TestHarnessDefenderChannelSupport.DefenderChannel channel,
            int limit,
            int attack,
            int defense,
            int technique,
            int speed,
            int stamina,
            int mentality,
            String message,
            String expectedSignal) {

        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }

        return support.loadCareer(userId)
            .flatMap(career -> {
                MatchFixture fixture = career.getTournamentState().getFixtures().stream()
                    .filter(f -> f.getMatchId().equals(matchId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Match not found in current tournament: " + matchId));
                String userTeamId = career.getUserSessionTeamId();
                boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
                boolean userIsAway = fixture.getAwayTeamId().equals(userTeamId);
                if (!userIsHome && !userIsAway) {
                    return Mono.error(new IllegalArgumentException(
                        "Opponent weak defender channel lab requires a match involving the user team: " + userTeamId));
                }
                String opponentTeamId = userIsHome ? fixture.getAwayTeamId() : fixture.getHomeTeamId();
                List<SessionPlayer> opponentSquad = career.getTeamSquad(opponentTeamId);
                Map<String, LineupSlot> slots = career.getTeamStarting11SubdivisionSlots()
                    .getOrDefault(opponentTeamId, Map.of());

                List<SessionPlayer> affected = support.findStartingDefendersByChannel(
                    opponentSquad, slots, channel, limit);
                if (affected.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Opponent weak defender channel lab requires at least one opponent starting DEF in channel " + channel));
                }

                if (labKey.startsWith("prepare-")) {
                    support.rememberLabSnapshot(userId, snapshotKey, career, opponentTeamId, affected);
                } else {
                    Optional<LabMutationResult> restored = support.restoreLabSnapshot(
                        userId, snapshotKey, career, opponentTeamId, message);
                    if (restored.isPresent()) {
                        return support.persistLabMutation(career, restored.get());
                    }
                }

                affected.forEach(player ->
                    support.applyLabStats(player, attack, defense, technique, speed, stamina, mentality));
                if (labKey.startsWith("prepare-")) {
                    Map<String, LineupSlot> labSlots = new LinkedHashMap<>(slots);
                    for (int i = 0; i < affected.size(); i++) {
                        SessionPlayer player = affected.get(i);
                        TestHarnessDefenderChannelSupport.ChannelSlot channelSlot = support.slotForChannel(channel, i);
                        labSlots.put(player.getSessionPlayerId(), new LineupSlot(
                            player.getSessionPlayerId(),
                            channelSlot.subdivisionId(),
                            channelSlot.x(),
                            78.0));
                    }
                    career.replaceTeamStarting11SubdivisionRaw(opponentTeamId, labSlots);
                }

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("matchId", matchId);
                details.put("userTeamId", userTeamId);
                details.put("opponentTeamId", opponentTeamId);
                details.put("channel", channel.name());
                details.put("affectedPlayers", affected.stream()
                    .map(support::labPlayerDetails)
                    .toList());
                details.put("slotMutation", labKey.startsWith("prepare-")
                    ? "opponent DEF forced into " + channel.name().toLowerCase() + " defensive channel for offensive exploitation smoke"
                    : "opponent DEF channel lab restored from snapshot when available");
                details.put("expectedFocus", List.of("CENTRAL_PLAY", "WIDE_PLAY"));
                details.put("expectedSignal", expectedSignal);

                log.info("{} userId={} match={} opponentTeam={} channel={} affected={}",
                    labKey, userId, matchId, opponentTeamId, channel,
                    affected.stream().map(SessionPlayer::getSessionPlayerId).toList());

                return support.persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }


}
