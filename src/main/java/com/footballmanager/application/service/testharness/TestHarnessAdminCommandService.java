package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.application.service.editor.FormationPosition;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.application.service.simulation.detailed.BaselineState;
import com.footballmanager.application.service.simulation.detailed.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEngine;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.detailed.LiveSession;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEventType;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.application.service.simulation.detailed.MatchLineupPlayerDto;
import com.footballmanager.application.service.simulation.detailed.PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.detailed.ShotLocation;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PositionEffectivenessCalculator;
import com.footballmanager.domain.port.in.testharness.CustomFixture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Profile({"dev", "local", "test"})
@Slf4j
class TestHarnessAdminCommandService {

    private final CareerRepository careerRepository;
    private final CareerSessionService careerSessionService;
    private final DetailedMatchStoragePort detailedMatchStoragePort;
    private final MatchEngineRegistry matchEngineRegistry;

    TestHarnessAdminCommandService(
            CareerRepository careerRepository,
            CareerSessionService careerSessionService,
            DetailedMatchStoragePort detailedMatchStoragePort,
            MatchEngineRegistry matchEngineRegistry) {
        this.careerRepository = careerRepository;
        this.careerSessionService = careerSessionService;
        this.detailedMatchStoragePort = detailedMatchStoragePort;
        this.matchEngineRegistry = matchEngineRegistry;
    }

    private Mono<CareerSave> loadCareer(UUID userId) {
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> optionalCareer
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new IllegalStateException(
                    "Career not found for userId=" + userId))));
    }

    public Mono<Void> replaceFixtures(UUID userId, List<CustomFixture> fixtures) {
        if (fixtures == null) {
            return Mono.error(new IllegalArgumentException("fixtures must be a non-null list"));
        }
        if (fixtures.isEmpty()) {
            log.info("replaceFixtures userId={} no-op (empty list, 0 fixtures to replace)",
                userId);
            return Mono.empty();
        }

        return loadCareer(userId)
            .flatMap(career -> executeReplaceFixtures(career, fixtures));
    }

private Mono<Void> executeReplaceFixtures(CareerSave career, List<CustomFixture> fixtures) {
        int maxRound = fixtures.stream().mapToInt(CustomFixture::round).max().orElse(1);

        List<MatchFixture> newFixtures = new ArrayList<>(fixtures.size());
        for (CustomFixture spec : fixtures) {
            String matchId = (spec.matchId() != null && !spec.matchId().isBlank())
                ? spec.matchId()
                : UUID.randomUUID().toString();
            newFixtures.add(new MatchFixture(
                matchId, spec.homeTeamId(), spec.awayTeamId(), spec.round()));
        }

        career.getTournamentState().setFixtures(newFixtures);
        career.getTournamentState().setCurrentRound(1);
        career.getTournamentState().setFinished(false);
        career.getTournamentState().setCareerPhase(CareerPhase.PRE_MATCH);
        career.getTournamentState().initializeStandings(career.getAllSessionTeams());
        career.getTournamentState().setTotalRounds(maxRound);

        log.info("replaceFixtures userId={} count={} maxRound={}",
            career.getUserId(), fixtures.size(), maxRound);
        return careerSessionService.saveCareer(career).then()
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    public Mono<Void> resetInjuries(UUID userId) {
        return loadCareer(userId)
            .flatMap(this::executeResetInjuries);
    }

private Mono<Void> executeResetInjuries(CareerSave career) {
        String userSessionTeamId = career.getUserSessionTeamId();
        List<SessionPlayer> squad = career.getTeamSquad(userSessionTeamId);

        int cleared = 0;
        for (SessionPlayer p : squad) {
            if (Boolean.TRUE.equals(p.getInjured())
                || Boolean.TRUE.equals(p.getSuspended())
                || (p.getYellowCards() != null && p.getYellowCards() > 0)
                || (p.getRedCards() != null && p.getRedCards() > 0)) {
                p.setInjured(false);
                p.setInjuryType(null);
                p.setInjuryRemainingMatches(0);
                p.setSuspended(false);
                p.setSuspensionRemainingMatches(0);
                p.setYellowCards(0);
                p.setRedCards(0);
                cleared++;
            }
        }

        log.trace("resetInjuries userId={} squadSize={} cleared={}",
            career.getUserId(), squad.size(), cleared);

        return careerSessionService.saveCareer(career).then()
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    public Mono<Void> setFormation(UUID userId, String formation) {
        if (formation == null || formation.isBlank()) {
            return Mono.error(new IllegalArgumentException("formation must be non-blank"));
        }

        return loadCareer(userId)
            .flatMap(career -> executeSetFormation(career, formation));
    }

private Mono<Void> executeSetFormation(CareerSave career, String formation) {
        String userSessionTeamId = career.getUserSessionTeamId();

        SessionTeam userTeam = career.getSessionTeam(userSessionTeamId);
        if (userTeam == null) {
            return Mono.error(new IllegalStateException(
                "User session team not found: " + userSessionTeamId));
        }
        userTeam.setFormation(formation);
        career.getTeamStarting11Formation().put(userSessionTeamId, formation);

        log.trace("setFormation userId={} team={} formation={}",
            career.getUserId(), userSessionTeamId, formation);

        return careerSessionService.saveCareer(career).then()
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

public Mono<Void> setStyle(UUID userId, TeamStyle style) {
        if (style == null) {
            return Mono.error(new IllegalArgumentException("style must be non-null"));
        }

        return loadCareer(userId)
            .flatMap(career -> executeSetStyle(career, style));
    }

private Mono<Void> executeSetStyle(CareerSave career, TeamStyle style) {
        String userSessionTeamId = career.getUserSessionTeamId();

        SessionTeam userTeam = career.getSessionTeam(userSessionTeamId);
        if (userTeam == null) {
            return Mono.error(new IllegalStateException(
                "User session team not found: " + userSessionTeamId));
        }
        userTeam.setStyle(style);

        log.info("setStyle userId={} team={} style={}",
            career.getUserId(), userSessionTeamId, style);

        return careerSessionService.saveCareer(career).then()
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    public Mono<Void> injectPlayerStats(UUID userId, String playerId,
                                       Integer attack, Integer defense,
                                       Integer technique, Integer speed,
                                       Integer stamina, Integer mentality,
                                       Integer heightCm,
                                       Map<PlayerSkill, Integer> skillLevels) {
        if (playerId == null || playerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("playerId must be non-blank"));
        }
        String rangeErr = validateStatRanges(attack, defense, technique, speed, stamina, mentality);
        if (rangeErr != null) {
            return Mono.error(new IllegalArgumentException(rangeErr));
        }
        if (heightCm != null && (heightCm < 160 || heightCm > 210)) {
            return Mono.error(new IllegalArgumentException(
                "heightCm out of range [160, 210]: " + heightCm));
        }
        if (skillLevels != null && !skillLevels.isEmpty()) {
            for (Map.Entry<PlayerSkill, Integer> e : skillLevels.entrySet()) {
                if (e.getKey() != null && e.getValue() != null
                    && (e.getValue() < 0 || e.getValue() > 99)) {
                    return Mono.error(new IllegalArgumentException(
                        "Skill level out of range [0, 99] for " + e.getKey() + ": " + e.getValue()));
                }
            }
        }

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return executeInjectPlayerStats(career, playerId,
                    attack, defense, technique, speed, stamina, mentality,
                    heightCm, skillLevels);
            });
    }

private static String validateStatRanges(Integer attack, Integer defense,
                                              Integer technique, Integer speed,
                                              Integer stamina, Integer mentality) {
        Integer[] stats = {attack, defense, technique, speed, stamina, mentality};
        String[] names = {"attack", "defense", "technique", "speed", "stamina", "mentality"};
        for (int i = 0; i < stats.length; i++) {
            Integer v = stats[i];
            if (v != null && (v < 0 || v > 99)) {
                return names[i] + " out of range [0, 99]: " + v;
            }
        }
        return null;
    }

private Mono<Void> executeInjectPlayerStats(CareerSave career, String playerId,
                                                 Integer attack, Integer defense,
                                                 Integer technique, Integer speed,
                                                 Integer stamina, Integer mentality,
                                                 Integer heightCm,
                                                 Map<PlayerSkill, Integer> skillLevels) {
        SessionPlayer target = career.getSessionPlayers().get(playerId);
        if (target == null) {
            return Mono.error(new IllegalArgumentException(
                "Player not found in current career: " + playerId
                + " (available: " + career.getSessionPlayers().size() + " players)"));
        }
        StringBuilder logMsg = new StringBuilder();
        if (attack != null) { target.setAttack(attack); logMsg.append(" attack=").append(attack); }
        if (defense != null) { target.setDefense(defense); logMsg.append(" defense=").append(defense); }
        if (technique != null) { target.setTechnique(technique); logMsg.append(" technique=").append(technique); }
        if (speed != null) { target.setSpeed(speed); logMsg.append(" speed=").append(speed); }
        if (stamina != null) { target.setStamina(stamina); logMsg.append(" stamina=").append(stamina); }
        if (mentality != null) { target.setMentality(mentality); logMsg.append(" mentality=").append(mentality); }
        if (heightCm != null) {
            target.setHeightCm(heightCm);
            logMsg.append(" heightCm=").append(heightCm);
        }
        if (skillLevels != null && !skillLevels.isEmpty()) {
            int skillCount = 0;
            for (Map.Entry<PlayerSkill, Integer> entry : skillLevels.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                target.setSkillLevel(entry.getKey(), entry.getValue());
                skillCount++;
                logMsg.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
            }
            logMsg.append(" (skills=").append(skillCount).append(')');
        }

        log.info("injectPlayerStats userId={} player={} ({}){}",
            career.getUserId(), playerId, target.getName(), logMsg);

        return careerSessionService.saveCareer(career).then()
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    public Mono<Void> resetRound(UUID userId, String roundId) {
        if (roundId == null || roundId.isBlank()) {
            return Mono.error(new IllegalArgumentException("roundId is required and must be non-blank"));
        }
        log.info("resetRound userId={}, roundId={}",
            userId, roundId);

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return executeResetRound(career, roundId);
            });
    }

private Mono<Void> executeResetRound(CareerSave career, String roundId) {
        String careerId = career.getCareerId();
        int totalRounds = career.getTournamentState().getTotalRounds();
        int round = deriveRoundFromUuid(roundId, careerId, totalRounds);
        if (round < 1) {
            return Mono.error(new IllegalArgumentException(
                "roundId " + roundId + " does not match any round of career " + careerId
                + " (1.." + totalRounds + ")"));
        }

        List<MatchFixture> roundFixtures = new ArrayList<>();
        for (MatchFixture f : career.getTournamentState().getFixtures()) {
            if (f.getRound() == round) {
                roundFixtures.add(f);
            }
        }

        if (roundFixtures.isEmpty()) {
            return Mono.error(new IllegalStateException(
                "No fixtures found for round " + round
                + " (career has " + career.getTournamentState().getFixtures().size()
                + " fixtures across " + totalRounds + " rounds)"));
        }

        int resetCount = 0;
        int removedEngines = 0;
        List<Mono<Void>> detailDeletes = new ArrayList<>();
        UUID userId = career.getUserId();

        for (MatchFixture fixture : roundFixtures) {
            String matchId = fixture.getMatchId();
            boolean wasCompleted = fixture.isCompleted();
            fixture.reset();
            resetCount++;
            try {
                if (matchEngineRegistry.hasEngine(userId, UUID.fromString(matchId))) {
                    matchEngineRegistry.stopAndRemoveEngine(userId, UUID.fromString(matchId));
                    removedEngines++;
                }
            } catch (Exception e) {
                log.warn("resetRound: failed to remove engine for matchId={}: {}",
                    matchId, e.getMessage());
            }
            try {
                detailDeletes.add(detailedMatchStoragePort.deleteByMatchId(careerId, matchId)
                    .onErrorResume(e -> {
                        log.warn("resetRound: failed to clear detailed match detail for matchId={}: {}",
                            matchId, e.getMessage());
                        return Mono.empty();
                    }));
            } catch (Exception e) {
                log.warn("resetRound: failed to clear detailed match detail for matchId={}: {}",
                    matchId, e.getMessage());
            }

            log.info("resetRound matchId={} round={} wasCompleted={}",
                matchId, fixture.getRound(), wasCompleted);
        }
        int previousCurrentRound = career.getTournamentState().getCurrentRound();
        if (previousCurrentRound != round) {
            log.info("resetRound rewinding currentRound: {} -> {} "
                + "(orchestrator only processes currentRound={} matchResults)",
                previousCurrentRound, round, round);
            career.getTournamentState().setCurrentRound(round);
        }
        career.getTournamentState().setCareerPhase(
            com.footballmanager.domain.model.entity.CareerPhase.PRE_MATCH);

        log.info("resetRound complete careerId={} roundId={} round={} resetFixtures={} removedEngines={} clearedDetails={} rewoundFrom={}",
            careerId, roundId, round, resetCount, removedEngines, detailDeletes.size(), previousCurrentRound);

        return Mono.whenDelayError(detailDeletes)
            .then(careerSessionService.saveCareer(career).then())
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

private int deriveRoundFromUuid(String roundId, String careerId, int totalRounds) {
        try {
            UUID target = UUID.fromString(roundId);
            for (int r = 1; r <= totalRounds; r++) {
                String candidate = com.footballmanager.application.service.query.FixtureQueryHelper
                    .deriveRoundId(careerId, r);
                if (candidate != null && candidate.equals(target.toString())) {
                    return r;
                }
            }
            return -1;
        } catch (Exception e) {
            log.warn("deriveRoundFromUuid failed for roundId={}, careerId={}: {}",
                roundId, careerId, e.getMessage());
            return -1;
        }
    }

}

