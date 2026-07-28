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
class TestHarnessUserDefenderChannelLabService {

    private final TestHarnessDefenderChannelSupport support;

    public Mono<LabMutationResult> prepareWeakLeftDefenderLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-left-defender",
            "prepare-weak-left-defender-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.LEFT,
            1,
            45, 25, 45, 45, 55, 25,
            "Prepared weak left defender lab: left-side DEF made vulnerable");
    }

    public Mono<LabMutationResult> restoreWeakLeftDefenderLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-left-defender",
            "restore-weak-left-defender-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.LEFT,
            1,
            76, 76, 76, 76, 76, 76,
            "Restored weak left defender lab players to smoke defaults");
    }

    public Mono<LabMutationResult> prepareWeakRightDefenderLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-right-defender",
            "prepare-weak-right-defender-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.RIGHT,
            1,
            45, 25, 45, 45, 55, 25,
            "Prepared weak right defender lab: right-side DEF made vulnerable");
    }

    public Mono<LabMutationResult> restoreWeakRightDefenderLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-right-defender",
            "restore-weak-right-defender-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.RIGHT,
            1,
            76, 76, 76, 76, 76, 76,
            "Restored weak right defender lab players to smoke defaults");
    }

    public Mono<LabMutationResult> prepareWeakCenterBacksLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-center-backs",
            "prepare-weak-center-backs-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.CENTER,
            2,
            45, 25, 45, 45, 55, 25,
            "Prepared weak center backs lab: central DEF made vulnerable");
    }

    public Mono<LabMutationResult> restoreWeakCenterBacksLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-center-backs",
            "restore-weak-center-backs-lab",
            TestHarnessDefenderChannelSupport.DefenderChannel.CENTER,
            2,
            76, 76, 76, 76, 76, 76,
            "Restored weak center backs lab players to smoke defaults");
    }



    private Mono<LabMutationResult> mutateWeakDefenderChannelLab(
            UUID userId,
            String labName,
            String labKey,
            TestHarnessDefenderChannelSupport.DefenderChannel channel,
            int limit,
            int attack,
            int defense,
            int technique,
            int speed,
            int stamina,
            int mentality,
            String message) {

        return support.loadCareer(userId)
            .flatMap(career -> {
                String userTeamId = career.getUserSessionTeamId();
                List<SessionPlayer> userSquad = career.getTeamSquad(userTeamId);
                Map<String, LineupSlot> slots = career.getTeamStarting11SubdivisionSlots()
                    .getOrDefault(userTeamId, Map.of());

                List<SessionPlayer> affected = support.findStartingDefendersByChannel(
                    userSquad, slots, channel, limit);
                if (affected.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        labName + " requires at least one starting DEF in channel " + channel));
                }

                if (labKey.startsWith("prepare-")) {
                    support.rememberLabSnapshot(userId, labName, career, userTeamId, affected);
                } else {
                    Optional<LabMutationResult> restored = support.restoreLabSnapshot(
                        userId, labName, career, userTeamId, message);
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
                    career.replaceTeamStarting11SubdivisionRaw(userTeamId, labSlots);
                }

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("userTeamId", userTeamId);
                details.put("channel", channel.name());
                details.put("affectedPlayers", affected.stream()
                    .map(support::labPlayerDetails)
                    .toList());
                details.put("slotMutation", labKey.startsWith("prepare-")
                    ? "affected DEF forced into " + channel.name().toLowerCase() + " defensive channel"
                    : "fallback smoke defaults restored; exact snapshot unavailable");
                details.put("expectedScenarios", channel == TestHarnessDefenderChannelSupport.DefenderChannel.CENTER
                    ? List.of("m45-opponent-central")
                    : List.of("m45-opponent-wide"));

                log.info("{} userId={} team={} channel={} affected={}",
                    labKey, userId, userTeamId, channel,
                    affected.stream().map(SessionPlayer::getSessionPlayerId).toList());

                return support.persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }



}
