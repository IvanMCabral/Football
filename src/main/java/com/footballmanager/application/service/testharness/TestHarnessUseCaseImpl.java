package com.footballmanager.application.service.testharness;

import com.footballmanager.adapters.in.web.career.lineup.dto.FormationDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationPositionDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.application.service.simulation.v24.BaselineState;
import com.footballmanager.application.service.simulation.v24.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngine;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.application.service.simulation.v24.V24MatchLineupPlayerDto;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.v24.V24ShotLocation;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PositionEffectivenessCalculator;
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * V24D20-TESTHARNESS — Impl of {@link TestHarnessUseCase}.
 *
 * <p>All methods assume the caller has already been authenticated
 * (the controller uses {@code controllerHelper.getUserId(authentication)})
 * and is operating within a {@code dev|local|test} profile context.
 *
 * <p><b>Determinism guarantee:</b> when {@code app.simulation.random-seed}
 * is set to a fixed value (e.g. 42L), match simulation becomes reproducible
 * — same squads + same fixtures + same formation + same seed ⇒ same outcome.
 * This is what enables Bloque B (same match × N runs ⇒ similar results).
 *
 * <p><b>CareerSave manipulation pattern:</b> load via repository → mutate
 * state in-memory → {@code careerRepository.save(career)}. The save
 * pipeline re-serializes via Jackson so all Redis round-trip semantics are
 * preserved (no shape drift).
 */
@Service
@Profile({"dev", "local", "test"})
@Slf4j
@RequiredArgsConstructor
public class TestHarnessUseCaseImpl implements TestHarnessUseCase {

    private static final String AUTO_POSITION_PIXEL_PREFIX = "__AUTO_";
    private static final String AUTO_PLAYER_SWAP_STARTER = "__AUTO_STARTER";
    private static final String AUTO_PLAYER_SWAP_BENCH = "__AUTO_BENCH";
    private static final String AUTO_PLAYER_SWAP_PREFIX = "__AUTO_SWAP_";

    private final CareerRepository careerRepository;
    private final CareerSessionService careerSessionService;
    // V24D20-SANDBOX-V2-MVP F5: replay endpoint dependencies
    private final V24MatchContextFactory v24ContextFactory;
    private final V24DetailedMatchStoragePort v24StoragePort;
    private final BaselineStateStoragePort baselineStoragePort;
    private final FormationService formationService = new FormationService();
    // V24D24.3-HOTFIX: resetRound needs to evict cached MatchSessions so
    // the next /match-engine/rounds/start call rebuilds the engine from
    // scratch (see MatchEngineRegistry.startEngine line 25-30 for the
    // guard we're working around).
    private final MatchEngineRegistry matchEngineRegistry;

    /**
     * Dev/local harness safety net: labs mutate Redis career state, so prepare
     * stores exact player stats + lineup slots in-memory and restore puts them
     * back. This keeps experiments reversible while the backend process is
     * alive. If the process restarts between prepare/restore, the individual
     * restore methods still use their legacy smoke defaults as a fallback.
     */
    private final ConcurrentMap<String, LabSnapshot> labSnapshots = new ConcurrentHashMap<>();

    // ========== replaceFixtures ==========

    @Override
    public Mono<Void> replaceFixtures(UUID userId, List<CustomFixture> fixtures) {
        // V24D24.1 — BUG_TESTHARNESS_REPLACE_FIXTURES_REJECTS_EMPTY:
        // null guard stays (genuine client-error → 400), empty list is
        // treated as a no-op so the test-harness frontend can "skip
        // replacement" when the preset builder returns [] (F2 scope
        // placeholder). Without this, the smoke REVISOR cannot drive the
        // single-match preset path.
        if (fixtures == null) {
            return Mono.error(new IllegalArgumentException("fixtures must be a non-null list"));
        }
        if (fixtures.isEmpty()) {
            log.info("[V24D24.1] replaceFixtures userId={} no-op (empty list, 0 fixtures to replace)",
                userId);
            return Mono.empty();
        }

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return executeReplaceFixtures(career, fixtures);
            });
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
        // V24D20-SANDBOX-V2-MVP BUG #2: setTotalRounds is the LAST write so
        // any future side-effect on setFixtures / setCurrentRound /
        // setFinished / setCareerPhase / initializeStandings cannot clobber
        // it. The invariant is totalRounds == max(fixtures.round).
        career.getTournamentState().setTotalRounds(maxRound);

        log.info("[V24D20-TESTHARNESS] replaceFixtures userId={} count={} maxRound={}",
            career.getUserId(), fixtures.size(), maxRound);

        // V24D20-SANDBOX-V2-MVP BUG #1: invalidate CareerSessionService cache
        // so the next getCareerFromCache(userId) returns the updated career,
        // not the stale in-memory copy. Without this, the V24 engine sees
        // the OLD fixtures.
        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    // ========== resetInjuries ==========

    @Override
    public Mono<Void> resetInjuries(UUID userId) {
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return executeResetInjuries(career);
            });
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

        log.trace("[V24D20-TESTHARNESS] resetInjuries userId={} squadSize={} cleared={}",
            career.getUserId(), squad.size(), cleared);

        // V24D20-SANDBOX-V2-MVP BUG #1: invalidate cache after save
        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    // ========== setFormation ==========

    @Override
    public Mono<Void> setFormation(UUID userId, String formation) {
        if (formation == null || formation.isBlank()) {
            return Mono.error(new IllegalArgumentException("formation must be non-blank"));
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
                return executeSetFormation(career, formation);
            });
    }

    /**
     * V24D20-TESTHARNESS — persist formation to BOTH the {@code SessionTeam.formation}
     * field AND the {@code teamStarting11Formation} map.
     *
     * <p>CRITICAL: the V24 engine reads formation from
     * {@code career.getTeamStarting11Formation().get(userSessionTeamId)} —
     * NOT from {@code sessionTeam.getFormation()}. The 1.7 sprint regression
     * (BUG_FORMATION_PERSIST_IGNORED) was caused by writing only to
     * {@code SessionTeam.formation} (the simulation never picked it up).
     *
     * <p>Both writes must succeed for the smoke harness to drive formation
     * changes that the engine actually respects.
     */
    private Mono<Void> executeSetFormation(CareerSave career, String formation) {
        String userSessionTeamId = career.getUserSessionTeamId();

        SessionTeam userTeam = career.getSessionTeam(userSessionTeamId);
        if (userTeam == null) {
            return Mono.error(new IllegalStateException(
                "User session team not found: " + userSessionTeamId));
        }
        userTeam.setFormation(formation);

        // CRITICAL: the V24 engine reads from this map, not from
        // SessionTeam.formation. Setting only the SessionTeam was the
        // BUG_FORMATION_PERSIST_IGNORED root cause in sprint 1.7.
        career.getTeamStarting11Formation().put(userSessionTeamId, formation);

        log.trace("[V24D20-TESTHARNESS] setFormation userId={} team={} formation={}",
            career.getUserId(), userSessionTeamId, formation);

        // V24D20-SANDBOX-V2-MVP BUG #1: invalidate cache after save
        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    // ========== setStyle (V25D28) ==========

    /**
     * V25D28: set the user team's tactical style (BALANCED, ATTACKING, DEFENSIVE,
     * COUNTER, POSSESSION). Persists to {@code SessionTeam.style} so that the
     * V24 engine can read it via {@link com.footballmanager.application.service.simulation.v24.V24MatchContextFactory#build}
     * in the test-harness replay path.
     *
     * <p>Note: unlike {@code setFormation}, the engine does NOT read style from
     * {@code teamStarting11Formation} map — style is a single per-team value, so
     * persisting it on SessionTeam directly is sufficient (consistent with the
     * pre-V24D14 sprint 1.5 save format).
     */
    public Mono<Void> setStyle(UUID userId, TeamStyle style) {
        if (style == null) {
            return Mono.error(new IllegalArgumentException("style must be non-null"));
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
                return executeSetStyle(career, style);
            });
    }

    private Mono<Void> executeSetStyle(CareerSave career, TeamStyle style) {
        String userSessionTeamId = career.getUserSessionTeamId();

        SessionTeam userTeam = career.getSessionTeam(userSessionTeamId);
        if (userTeam == null) {
            return Mono.error(new IllegalStateException(
                "User session team not found: " + userSessionTeamId));
        }
        userTeam.setStyle(style);

        log.info("[V25D28-TESTHARNESS] setStyle userId={} team={} style={}",
            career.getUserId(), userSessionTeamId, style);

        // V24D20-SANDBOX-V2-MVP BUG #1: invalidate cache after save
        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    // ========== injectPlayerStats (V25D29 + V25D35 extension) ==========

    /**
     * V25D29: mutate one SessionPlayer's stats in the persisted career. Null
     * stat args are left unchanged. Bounds-checked to {@code [0, 99]} (V25D25
     * engine convention).
     *
     * <p>V25D35 extension: also accepts two optional fields for the V25D31
     * physical + skill metadata:
     * <ul>
     *   <li>{@code heightCm} — nullable; bounds-checked to {@code [160, 210]}
     *       (matches {@link SessionPlayer#setHeightCm}). Null = leave
     *       current value unchanged (sparse semantics: setting to null
     *       would erase it, so null in the request = no-op).</li>
     *   <li>{@code skillLevels} — nullable sparse {@code Map<PlayerSkill, Integer>};
     *       each entry is bounds-checked to {@code [0, 99]} (matches
     *       {@link SessionPlayer#setSkillLevel}). Null OR empty map = no-op
     *       (does NOT clear existing skills — sparse map semantics).
     *       Setting a skill to {@code 0} via the map removes that entry
     *       from the sparse map (bit-a-bit consistent with
     *       {@code SessionPlayer.setSkillLevel(skill, 0)}).</li>
     * </ul>
     *
     * <p>Note: mutates a {@code SessionPlayer} inside the {@code
     * career.allSessionPlayers} map (engine reads from there). Mutates ALL
     * copies of the player — both the team-roster copy AND any bench copy
     * with the same {@code sessionPlayerId}.
     *
     * <p>Backward-compat: callers built against V25D29 (only 6 stats + playerId,
     * with {@code heightCm=null} and {@code skillLevels=null}) keep working
     * bit-a-bit — the new fields are skipped entirely.
     */
    @Override
    public Mono<Void> injectPlayerStats(UUID userId, String playerId,
                                       Integer attack, Integer defense,
                                       Integer technique, Integer speed,
                                       Integer stamina, Integer mentality,
                                       Integer heightCm,
                                       Map<PlayerSkill, Integer> skillLevels) {
        if (playerId == null || playerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("playerId must be non-blank"));
        }

        // Bounds check. Reject out-of-range values BEFORE the career load so
        // we don't partially mutate the career.
        String rangeErr = validateStatRanges(attack, defense, technique, speed, stamina, mentality);
        if (rangeErr != null) {
            return Mono.error(new IllegalArgumentException(rangeErr));
        }
        // V25D35: also bounds-check heightCm and skillLevels BEFORE the career load
        // so out-of-range values reject cleanly without partial mutation. The
        // SessionPlayer setters (setHeightCm / setSkillLevel) also throw IAE on
        // out-of-range, but those errors would surface AFTER findById — duplicating
        // the check here keeps the pre-load validation pattern consistent with
        // validateStatRanges above.
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

        // V25D35: physical + skill metadata. heightCm is sparse (null = leave
        // current value). skillLevels: null OR empty = no-op; otherwise iterate
        // each entry through SessionPlayer.setSkillLevel which bounds-checks
        // [0, 99] and treats 0 as "remove from sparse map".
        if (heightCm != null) {
            target.setHeightCm(heightCm);
            logMsg.append(" heightCm=").append(heightCm);
        }
        if (skillLevels != null && !skillLevels.isEmpty()) {
            int skillCount = 0;
            for (Map.Entry<PlayerSkill, Integer> entry : skillLevels.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    // Skip null entries defensively — Jackson may emit them
                    // if the caller sent {"HEADER": null} or {"null": 80}.
                    // SessionPlayer.setSkillLevel would throw IAE on null.
                    continue;
                }
                target.setSkillLevel(entry.getKey(), entry.getValue());
                skillCount++;
                logMsg.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
            }
            logMsg.append(" (skills=").append(skillCount).append(')');
        }

        log.info("[V25D35-TESTHARNESS] injectPlayerStats userId={} player={} ({}){}",
            career.getUserId(), playerId, target.getName(), logMsg);

        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    @Override
    public Mono<LabMutationResult> prepareOffensiveUpgradeLab(UUID userId) {
        return mutateOffensiveUpgradeLab(
            userId,
            "prepare-offensive-upgrade-lab",
            65, 65, 65, 65, 88, 65,
            99, 80, 99, 99, 99, 99,
            "Prepared offensive upgrade lab: current offensive starter down, matching bench attacker up");
    }

    @Override
    public Mono<LabMutationResult> restoreOffensiveUpgradeLab(UUID userId) {
        return mutateOffensiveUpgradeLab(
            userId,
            "restore-offensive-upgrade-lab",
            88, 88, 88, 88, 88, 88,
            78, 78, 78, 78, 78, 78,
            "Restored offensive upgrade lab players");
    }

    private Mono<LabMutationResult> mutateOffensiveUpgradeLab(
            UUID userId,
            String labKey,
            int mbappeAttack,
            int mbappeDefense,
            int mbappeTechnique,
            int mbappeSpeed,
            int mbappeStamina,
            int mbappeMentality,
            int endrickAttack,
            int endrickDefense,
            int endrickTechnique,
            int endrickSpeed,
            int endrickStamina,
            int endrickMentality,
            String message) {

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                String userTeamId = career.getUserSessionTeamId();
                List<SessionPlayer> userSquad = career.getTeamSquad(userTeamId);
                LabPair offensivePair = chooseOffensiveLabPair(career, userTeamId, userSquad)
                    .orElse(null);
                if (offensivePair == null) {
                    return Mono.error(new IllegalStateException(
                        "Offensive upgrade lab requires one offensive starter and one offensive bench player in the user squad"));
                }
                SessionPlayer starter = offensivePair.starter();
                SessionPlayer bench = offensivePair.bench();

                if (labKey.startsWith("prepare-")) {
                    rememberLabSnapshot(userId, "offensive-upgrade", career, userTeamId,
                        List.of(starter, bench));
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, "offensive-upgrade", career, userTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }

                applyLabStats(starter, mbappeAttack, mbappeDefense, mbappeTechnique, mbappeSpeed, mbappeStamina, mbappeMentality);
                applyLabStats(bench, endrickAttack, endrickDefense, endrickTechnique, endrickSpeed, endrickStamina, endrickMentality);

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("userTeamId", userTeamId);
                details.put("starterCandidate", labPlayerDetails(starter));
                details.put("benchCandidate", labPlayerDetails(bench));
                details.put("expectedScenario", "m60-offensive-upgrade-sub");
                details.put("expectedChange", safeName(starter) + " (" + starter.getPosition()
                    + ") -> " + safeName(bench) + " (" + bench.getPosition() + ")");

                log.info("[V25D99.22.9-TESTHARNESS] {} userId={} team={} starter={} bench={}",
                    labKey, userId, userTeamId, starter.getSessionPlayerId(), bench.getSessionPlayerId());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }

    private Optional<LabPair> chooseOffensiveLabPair(
            CareerSave career,
            String userTeamId,
            List<SessionPlayer> userSquad) {
        if (career == null || userTeamId == null || userSquad == null || userSquad.isEmpty()) {
            return Optional.empty();
        }

        SessionPlayer namedStarter = findSquadPlayerByName(userSquad, "Kylian Mbappe");
        SessionPlayer namedBench = findSquadPlayerByName(userSquad, "Endrick");
        if (namedStarter != null && namedBench != null) {
            return Optional.of(new LabPair(namedStarter, namedBench));
        }

        Set<String> startingIds = Set.copyOf(
            career.getTeamStarting11().getOrDefault(userTeamId, List.of()));
        if (startingIds.isEmpty()) {
            return Optional.empty();
        }

        List<SessionPlayer> offensiveStarters = userSquad.stream()
            .filter(this::isOutfieldPlayer)
            .filter(p -> startingIds.contains(p.getSessionPlayerId()))
            .filter(p -> "ATT".equals(p.getPosition()) || "WINGER".equals(p.getPosition()))
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> "ATT".equals(p.getPosition()) ? 0 : 1)
                .thenComparingInt((SessionPlayer p) -> -substitutionScore(p))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .toList();

        for (SessionPlayer starter : offensiveStarters) {
            Optional<SessionPlayer> bench = userSquad.stream()
                .filter(this::isOutfieldPlayer)
                .filter(p -> !startingIds.contains(p.getSessionPlayerId()))
                .filter(p -> starter.getPosition() != null && starter.getPosition().equals(p.getPosition()))
                .sorted(Comparator
                    .comparingInt((SessionPlayer p) -> substitutionScore(p))
                    .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
                .findFirst();
            if (bench.isPresent()) {
                return Optional.of(new LabPair(starter, bench.get()));
            }
        }

        return Optional.empty();
    }

    @Override
    public Mono<LabMutationResult> prepareObjectiveContrastLab(UUID userId) {
        return mutateObjectiveContrastLab(
            userId,
            "prepare-objective-contrast-lab",
            "Prepared objective contrast lab: one attacking upside swap and one protective swap shaped for DT objective comparison",
            true);
    }

    @Override
    public Mono<LabMutationResult> restoreObjectiveContrastLab(UUID userId) {
        return mutateObjectiveContrastLab(
            userId,
            "restore-objective-contrast-lab",
            "Restored objective contrast lab players to exact snapshot",
            false);
    }

    private Mono<LabMutationResult> mutateObjectiveContrastLab(
            UUID userId,
            String labKey,
            String message,
            boolean prepare) {

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                String userTeamId = career.getUserSessionTeamId();
                List<SessionPlayer> userSquad = career.getTeamSquad(userTeamId);
                ObjectiveContrastLabPairs pairs = chooseObjectiveContrastLabPairs(career, userTeamId, userSquad)
                    .orElse(null);
                if (pairs == null) {
                    return Mono.error(new IllegalStateException(
                        "Objective contrast lab requires offensive and defensive starter/bench pairs in the user squad"));
                }

                List<SessionPlayer> affected = List.of(
                    pairs.offensiveStarter(),
                    pairs.offensiveBench(),
                    pairs.defensiveStarter(),
                    pairs.defensiveBench());

                if (prepare) {
                    rememberLabSnapshot(userId, "objective-contrast", career, userTeamId, affected);
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, "objective-contrast", career, userTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }

                // Offensive pair: starter is safe/limited, bench is explosive but less protective.
                applyLabStats(pairs.offensiveStarter(), 62, 70, 66, 68, 82, 78);
                applyLabStats(pairs.offensiveBench(), 98, 42, 94, 96, 78, 64);

                // Defensive pair: starter is more progressive but vulnerable, bench is conservative/protective.
                applyLabStats(pairs.defensiveStarter(), 72, 58, 78, 78, 82, 68);
                applyLabStats(pairs.defensiveBench(), 38, 98, 58, 74, 88, 96);

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("userTeamId", userTeamId);
                details.put("offensiveStarter", labPlayerDetails(pairs.offensiveStarter()));
                details.put("offensiveBench", labPlayerDetails(pairs.offensiveBench()));
                details.put("defensiveStarter", labPlayerDetails(pairs.defensiveStarter()));
                details.put("defensiveBench", labPlayerDetails(pairs.defensiveBench()));
                details.put("expectedAttackChange", safeName(pairs.offensiveStarter()) + " -> " + safeName(pairs.offensiveBench()));
                details.put("expectedProtectChange", safeName(pairs.defensiveStarter()) + " -> " + safeName(pairs.defensiveBench()));
                details.put("expectedHarnessRead", "Necesito gol should prefer attacking upside; Cuidar resultado should surface protective option if engine signal supports it.");

                log.info("[V25D99.38-TESTHARNESS] {} userId={} team={} attackPair={}->{} protectPair={}->{}",
                    labKey,
                    userId,
                    userTeamId,
                    pairs.offensiveStarter().getSessionPlayerId(),
                    pairs.offensiveBench().getSessionPlayerId(),
                    pairs.defensiveStarter().getSessionPlayerId(),
                    pairs.defensiveBench().getSessionPlayerId());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }

    private Optional<ObjectiveContrastLabPairs> chooseObjectiveContrastLabPairs(
            CareerSave career,
            String userTeamId,
            List<SessionPlayer> userSquad) {
        if (career == null || userTeamId == null || userSquad == null || userSquad.isEmpty()) {
            return Optional.empty();
        }
        Set<String> startingIds = Set.copyOf(
            career.getTeamStarting11().getOrDefault(userTeamId, List.of()));
        if (startingIds.isEmpty()) {
            return Optional.empty();
        }
        List<SessionPlayer> starters = userSquad.stream()
            .filter(this::isOutfieldPlayer)
            .filter(p -> startingIds.contains(p.getSessionPlayerId()))
            .toList();
        List<SessionPlayer> bench = userSquad.stream()
            .filter(this::isOutfieldPlayer)
            .filter(p -> !startingIds.contains(p.getSessionPlayerId()))
            .filter(p -> !Boolean.TRUE.equals(p.getInjured()) && !Boolean.TRUE.equals(p.getSuspended()))
            .toList();

        SessionPlayer offensiveStarter = starters.stream()
            .filter(p -> "ATT".equals(p.getPosition()) || "WINGER".equals(p.getPosition()))
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> "ATT".equals(p.getPosition()) ? 0 : 1)
                .thenComparingInt((SessionPlayer p) -> -substitutionScore(p))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .findFirst()
            .orElse(null);
        SessionPlayer offensiveBench = bench.stream()
            .filter(p -> offensiveStarter != null && offensiveStarter.getPosition() != null
                && offensiveStarter.getPosition().equals(p.getPosition()))
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> substitutionScore(p))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .findFirst()
            .orElseGet(() -> bench.stream()
                .filter(p -> "ATT".equals(p.getPosition()) || "WINGER".equals(p.getPosition()))
                .sorted(Comparator
                    .comparingInt((SessionPlayer p) -> substitutionScore(p))
                    .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
                .findFirst()
                .orElse(null));

        SessionPlayer defensiveStarter = starters.stream()
            .filter(p -> "DEF".equals(p.getPosition()))
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> p.getDefense())
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .findFirst()
            .orElse(null);
        SessionPlayer defensiveBench = bench.stream()
            .filter(p -> "DEF".equals(p.getPosition()))
            .filter(p -> defensiveStarter == null || !p.getSessionPlayerId().equals(defensiveStarter.getSessionPlayerId()))
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> substitutionScore(p))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .findFirst()
            .orElse(null);

        if (offensiveStarter == null || offensiveBench == null || defensiveStarter == null || defensiveBench == null) {
            return Optional.empty();
        }
        if (Set.of(
            offensiveStarter.getSessionPlayerId(),
            offensiveBench.getSessionPlayerId(),
            defensiveStarter.getSessionPlayerId(),
            defensiveBench.getSessionPlayerId()).size() < 4) {
            return Optional.empty();
        }
        return Optional.of(new ObjectiveContrastLabPairs(
            offensiveStarter,
            offensiveBench,
            defensiveStarter,
            defensiveBench));
    }

    @Override
    public Mono<LabMutationResult> prepareDefensiveDowngradeLab(UUID userId) {
        return mutateDefensiveDowngradeLab(
            userId,
            "prepare-defensive-downgrade-lab",
            88, 99, 88, 88, 99, 99,
            45, 35, 45, 45, 55, 35,
            "Prepared defensive downgrade lab: Carvajal strong, Fran Garcia weak");
    }

    @Override
    public Mono<LabMutationResult> restoreDefensiveDowngradeLab(UUID userId) {
        return mutateDefensiveDowngradeLab(
            userId,
            "restore-defensive-downgrade-lab",
            85, 85, 85, 85, 85, 85,
            78, 78, 78, 78, 78, 78,
            "Restored defensive downgrade lab players to smoke defaults");
    }

    private Mono<LabMutationResult> mutateDefensiveDowngradeLab(
            UUID userId,
            String labKey,
            int carvajalAttack,
            int carvajalDefense,
            int carvajalTechnique,
            int carvajalSpeed,
            int carvajalStamina,
            int carvajalMentality,
            int franAttack,
            int franDefense,
            int franTechnique,
            int franSpeed,
            int franStamina,
            int franMentality,
            String message) {

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                String userTeamId = career.getUserSessionTeamId();
                List<SessionPlayer> userSquad = career.getTeamSquad(userTeamId);
                SessionPlayer carvajal = findSquadPlayerByName(userSquad, "Dani Carvajal");
                SessionPlayer fran = findSquadPlayerByName(userSquad, "Fran Garcia");
                if (carvajal == null || fran == null) {
                    return Mono.error(new IllegalStateException(
                        "Defensive downgrade lab requires Dani Carvajal and Fran Garcia in the user squad"));
                }

                if (labKey.startsWith("prepare-")) {
                    rememberLabSnapshot(userId, "defensive-downgrade", career, userTeamId,
                        List.of(carvajal, fran));
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, "defensive-downgrade", career, userTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }

                applyLabStats(carvajal, carvajalAttack, carvajalDefense, carvajalTechnique,
                    carvajalSpeed, carvajalStamina, carvajalMentality);
                applyLabStats(fran, franAttack, franDefense, franTechnique,
                    franSpeed, franStamina, franMentality);

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("userTeamId", userTeamId);
                details.put("starterCandidate", labPlayerDetails(carvajal));
                details.put("benchCandidate", labPlayerDetails(fran));
                details.put("expectedScenario", "m60-defensive-downgrade-sub");
                details.put("expectedChange", "Dani Carvajal (DEF) -> Fran Garcia (DEF)");

                log.info("[V25D99.22.10-TESTHARNESS] {} userId={} team={} carvajal={} fran={}",
                    labKey, userId, userTeamId, carvajal.getSessionPlayerId(), fran.getSessionPlayerId());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }

    @Override
    public Mono<LabMutationResult> prepareWeakWideDefendersLab(UUID userId) {
        return mutateWeakWideDefendersLab(
            userId,
            "prepare-weak-wide-defenders-lab",
            45, 25, 45, 45, 55, 25,
            "Prepared weak wide defenders lab: current wide DEF starters made vulnerable");
    }

    @Override
    public Mono<LabMutationResult> restoreWeakWideDefendersLab(UUID userId) {
        return mutateWeakWideDefendersLab(
            userId,
            "restore-weak-wide-defenders-lab",
            76, 76, 76, 76, 76, 76,
            "Restored weak wide defenders lab players to smoke defaults");
    }

    @Override
    public Mono<LabMutationResult> prepareOpponentWeakWideDefendersLab(UUID userId, String matchId) {
        return mutateOpponentWeakWideDefendersLab(
            userId,
            matchId,
            "prepare-opponent-weak-wide-defenders-lab",
            45, 25, 45, 45, 55, 25,
            "Prepared opponent weak wide defenders lab: selected rival wide DEF starters made vulnerable");
    }

    @Override
    public Mono<LabMutationResult> restoreOpponentWeakWideDefendersLab(UUID userId, String matchId) {
        return mutateOpponentWeakWideDefendersLab(
            userId,
            matchId,
            "restore-opponent-weak-wide-defenders-lab",
            76, 76, 76, 76, 76, 76,
            "Restored opponent weak wide defenders lab players");
    }

    @Override
    public Mono<LabMutationResult> prepareOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-left-defender:" + matchId,
            "prepare-opponent-weak-left-defender-lab",
            DefenderChannel.LEFT,
            1,
            45, 25, 45, 45, 55, 25,
            "Prepared opponent weak left defender lab: selected rival left DEF made vulnerable",
            "RIGHT_FLANK/WIDE_PLAY should gain relative xG when the opponent left defender is vulnerable");
    }

    @Override
    public Mono<LabMutationResult> restoreOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-left-defender:" + matchId,
            "restore-opponent-weak-left-defender-lab",
            DefenderChannel.LEFT,
            1,
            76, 76, 76, 76, 76, 76,
            "Restored opponent weak left defender lab player",
            "Opponent left defender restored; attacking channel advantage should normalize");
    }

    @Override
    public Mono<LabMutationResult> prepareOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-right-defender:" + matchId,
            "prepare-opponent-weak-right-defender-lab",
            DefenderChannel.RIGHT,
            1,
            45, 25, 45, 45, 55, 25,
            "Prepared opponent weak right defender lab: selected rival right DEF made vulnerable",
            "LEFT_FLANK/WIDE_PLAY should gain relative xG when the opponent right defender is vulnerable");
    }

    @Override
    public Mono<LabMutationResult> restoreOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-right-defender:" + matchId,
            "restore-opponent-weak-right-defender-lab",
            DefenderChannel.RIGHT,
            1,
            76, 76, 76, 76, 76, 76,
            "Restored opponent weak right defender lab player",
            "Opponent right defender restored; attacking channel advantage should normalize");
    }

    @Override
    public Mono<LabMutationResult> prepareOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-center-backs:" + matchId,
            "prepare-opponent-weak-center-backs-lab",
            DefenderChannel.CENTER,
            2,
            45, 25, 45, 45, 55, 25,
            "Prepared opponent weak center backs lab: selected rival central DEF starters made vulnerable",
            "CENTRAL_PLAY should gain relative xG/xG-diff versus WIDE_PLAY when opponent center backs are vulnerable");
    }

    @Override
    public Mono<LabMutationResult> restoreOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return mutateOpponentWeakDefenderChannelLab(
            userId,
            matchId,
            "opponent-weak-center-backs:" + matchId,
            "restore-opponent-weak-center-backs-lab",
            DefenderChannel.CENTER,
            2,
            76, 76, 76, 76, 76, 76,
            "Restored opponent weak center backs lab players",
            "CENTRAL_PLAY should return to normal after restoring opponent center backs");
    }

    @Override
    public Mono<LabMutationResult> prepareWeakLeftDefenderLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-left-defender",
            "prepare-weak-left-defender-lab",
            DefenderChannel.LEFT,
            1,
            45, 25, 45, 45, 55, 25,
            "Prepared weak left defender lab: left-side DEF made vulnerable");
    }

    @Override
    public Mono<LabMutationResult> restoreWeakLeftDefenderLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-left-defender",
            "restore-weak-left-defender-lab",
            DefenderChannel.LEFT,
            1,
            76, 76, 76, 76, 76, 76,
            "Restored weak left defender lab players to smoke defaults");
    }

    @Override
    public Mono<LabMutationResult> prepareWeakRightDefenderLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-right-defender",
            "prepare-weak-right-defender-lab",
            DefenderChannel.RIGHT,
            1,
            45, 25, 45, 45, 55, 25,
            "Prepared weak right defender lab: right-side DEF made vulnerable");
    }

    @Override
    public Mono<LabMutationResult> restoreWeakRightDefenderLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-right-defender",
            "restore-weak-right-defender-lab",
            DefenderChannel.RIGHT,
            1,
            76, 76, 76, 76, 76, 76,
            "Restored weak right defender lab players to smoke defaults");
    }

    @Override
    public Mono<LabMutationResult> prepareWeakCenterBacksLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-center-backs",
            "prepare-weak-center-backs-lab",
            DefenderChannel.CENTER,
            2,
            45, 25, 45, 45, 55, 25,
            "Prepared weak center backs lab: central DEF made vulnerable");
    }

    @Override
    public Mono<LabMutationResult> restoreWeakCenterBacksLab(UUID userId) {
        return mutateWeakDefenderChannelLab(
            userId,
            "weak-center-backs",
            "restore-weak-center-backs-lab",
            DefenderChannel.CENTER,
            2,
            76, 76, 76, 76, 76, 76,
            "Restored weak center backs lab players to smoke defaults");
    }

    private Mono<LabMutationResult> mutateWeakWideDefendersLab(
            UUID userId,
            String labKey,
            int attack,
            int defense,
            int technique,
            int speed,
            int stamina,
            int mentality,
            String message) {

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                String userTeamId = career.getUserSessionTeamId();
                List<SessionPlayer> userSquad = career.getTeamSquad(userTeamId);
                Map<String, LineupSlotDTO> slots = career.getTeamStarting11SubdivisionSlots()
                    .getOrDefault(userTeamId, Map.of());

                List<SessionPlayer> wideDefenders = findWideStartingDefenders(userSquad, slots);
                if (wideDefenders.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Weak wide defenders lab requires at least one starting DEF in a wide slot"));
                }

                if (labKey.startsWith("prepare-")) {
                    rememberLabSnapshot(userId, "weak-wide-defenders", career, userTeamId, wideDefenders);
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, "weak-wide-defenders", career, userTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }

                wideDefenders.forEach(player ->
                    applyLabStats(player, attack, defense, technique, speed, stamina, mentality));
                if (labKey.startsWith("prepare-")) {
                    Map<String, LineupSlotDTO> labSlots = new LinkedHashMap<>(slots);
                    for (int i = 0; i < wideDefenders.size(); i++) {
                        SessionPlayer player = wideDefenders.get(i);
                        double x = i % 2 == 0 ? 18.0 : 82.0;
                        String subdivision = i % 2 == 0 ? "S22-1" : "S24-3";
                        labSlots.put(player.getSessionPlayerId(), new LineupSlotDTO(
                            player.getSessionPlayerId(),
                            subdivision,
                            x,
                            78.0));
                    }
                    career.replaceTeamStarting11SubdivisionRaw(userTeamId, labSlots);
                } else {
                    Map<String, LineupSlotDTO> restoredSlots = new LinkedHashMap<>(slots);
                    wideDefenders.forEach(player ->
                        restoredSlots.remove(player.getSessionPlayerId()));
                    career.replaceTeamStarting11SubdivisionRaw(userTeamId, restoredSlots);
                }

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("userTeamId", userTeamId);
                details.put("affectedPlayers", wideDefenders.stream()
                    .map(this::labPlayerDetails)
                    .toList());
                details.put("slotMutation", labKey.startsWith("prepare-")
                    ? "affected DEF forced to x18/x82 y78 for channel exposure"
                    : "affected DEF lab slots removed; other slots preserved");
                details.put("expectedScenarios", List.of("m45-opponent-wide", "m45-opponent-central"));
                details.put("expectedSignal", "m45-opponent-wide should increase opponent wide shots/xG against vulnerable fullbacks");

                log.info("[V25D99.22.16-TESTHARNESS] {} userId={} team={} affected={}",
                    labKey, userId, userTeamId,
                    wideDefenders.stream().map(SessionPlayer::getSessionPlayerId).toList());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }

    private Mono<LabMutationResult> mutateOpponentWeakWideDefendersLab(
            UUID userId,
            String matchId,
            String labKey,
            int attack,
            int defense,
            int technique,
            int speed,
            int stamina,
            int mentality,
            String message) {

        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
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
                        "Opponent weak wide defenders lab requires a match involving the user team: " + userTeamId));
                }
                String opponentTeamId = userIsHome ? fixture.getAwayTeamId() : fixture.getHomeTeamId();
                List<SessionPlayer> opponentSquad = career.getTeamSquad(opponentTeamId);
                Map<String, LineupSlotDTO> slots = career.getTeamStarting11SubdivisionSlots()
                    .getOrDefault(opponentTeamId, Map.of());

                List<SessionPlayer> wideDefenders = findWideStartingDefenders(opponentSquad, slots);
                if (wideDefenders.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Opponent weak wide defenders lab requires at least one opponent starting DEF in a wide slot"));
                }

                String snapshotKey = "opponent-weak-wide-defenders:" + matchId;
                if (labKey.startsWith("prepare-")) {
                    rememberLabSnapshot(userId, snapshotKey, career, opponentTeamId, wideDefenders);
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, snapshotKey, career, opponentTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }

                wideDefenders.forEach(player ->
                    applyLabStats(player, attack, defense, technique, speed, stamina, mentality));
                if (labKey.startsWith("prepare-")) {
                    Map<String, LineupSlotDTO> labSlots = new LinkedHashMap<>(slots);
                    for (int i = 0; i < wideDefenders.size(); i++) {
                        SessionPlayer player = wideDefenders.get(i);
                        double x = i % 2 == 0 ? 18.0 : 82.0;
                        String subdivision = i % 2 == 0 ? "S22-1" : "S24-3";
                        labSlots.put(player.getSessionPlayerId(), new LineupSlotDTO(
                            player.getSessionPlayerId(),
                            subdivision,
                            x,
                            78.0));
                    }
                    career.replaceTeamStarting11SubdivisionRaw(opponentTeamId, labSlots);
                } else {
                    Map<String, LineupSlotDTO> restoredSlots = new LinkedHashMap<>(slots);
                    wideDefenders.forEach(player ->
                        restoredSlots.remove(player.getSessionPlayerId()));
                    career.replaceTeamStarting11SubdivisionRaw(opponentTeamId, restoredSlots);
                }

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("matchId", matchId);
                details.put("userTeamId", userTeamId);
                details.put("opponentTeamId", opponentTeamId);
                details.put("affectedPlayers", wideDefenders.stream()
                    .map(this::labPlayerDetails)
                    .toList());
                details.put("slotMutation", labKey.startsWith("prepare-")
                    ? "opponent wide DEF forced to x18/x82 y78 for offensive wide-exploitation smoke"
                    : "opponent wide DEF lab slots removed; other slots preserved");
                details.put("expectedFocus", List.of("WIDE_PLAY", "CENTRAL_PLAY"));
                details.put("expectedSignal", "WIDE_PLAY should gain relative xG/xG-diff versus CENTRAL_PLAY when opponent wide defenders are vulnerable");

                log.info("[V25D99.64-TESTHARNESS] {} userId={} match={} opponentTeam={} affected={}",
                    labKey, userId, matchId, opponentTeamId,
                    wideDefenders.stream().map(SessionPlayer::getSessionPlayerId).toList());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }

    private Mono<LabMutationResult> mutateOpponentWeakDefenderChannelLab(
            UUID userId,
            String matchId,
            String snapshotKey,
            String labKey,
            DefenderChannel channel,
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

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
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
                Map<String, LineupSlotDTO> slots = career.getTeamStarting11SubdivisionSlots()
                    .getOrDefault(opponentTeamId, Map.of());

                List<SessionPlayer> affected = findStartingDefendersByChannel(
                    opponentSquad, slots, channel, limit);
                if (affected.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Opponent weak defender channel lab requires at least one opponent starting DEF in channel " + channel));
                }

                if (labKey.startsWith("prepare-")) {
                    rememberLabSnapshot(userId, snapshotKey, career, opponentTeamId, affected);
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, snapshotKey, career, opponentTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }

                affected.forEach(player ->
                    applyLabStats(player, attack, defense, technique, speed, stamina, mentality));
                if (labKey.startsWith("prepare-")) {
                    Map<String, LineupSlotDTO> labSlots = new LinkedHashMap<>(slots);
                    for (int i = 0; i < affected.size(); i++) {
                        SessionPlayer player = affected.get(i);
                        ChannelSlot channelSlot = slotForChannel(channel, i);
                        labSlots.put(player.getSessionPlayerId(), new LineupSlotDTO(
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
                    .map(this::labPlayerDetails)
                    .toList());
                details.put("slotMutation", labKey.startsWith("prepare-")
                    ? "opponent DEF forced into " + channel.name().toLowerCase() + " defensive channel for offensive exploitation smoke"
                    : "opponent DEF channel lab restored from snapshot when available");
                details.put("expectedFocus", List.of("CENTRAL_PLAY", "WIDE_PLAY"));
                details.put("expectedSignal", expectedSignal);

                log.info("[V25D99.66-TESTHARNESS] {} userId={} match={} opponentTeam={} channel={} affected={}",
                    labKey, userId, matchId, opponentTeamId, channel,
                    affected.stream().map(SessionPlayer::getSessionPlayerId).toList());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }

    private Mono<LabMutationResult> mutateWeakDefenderChannelLab(
            UUID userId,
            String labName,
            String labKey,
            DefenderChannel channel,
            int limit,
            int attack,
            int defense,
            int technique,
            int speed,
            int stamina,
            int mentality,
            String message) {

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId)))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                String userTeamId = career.getUserSessionTeamId();
                List<SessionPlayer> userSquad = career.getTeamSquad(userTeamId);
                Map<String, LineupSlotDTO> slots = career.getTeamStarting11SubdivisionSlots()
                    .getOrDefault(userTeamId, Map.of());

                List<SessionPlayer> affected = findStartingDefendersByChannel(
                    userSquad, slots, channel, limit);
                if (affected.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        labName + " requires at least one starting DEF in channel " + channel));
                }

                if (labKey.startsWith("prepare-")) {
                    rememberLabSnapshot(userId, labName, career, userTeamId, affected);
                } else {
                    Optional<LabMutationResult> restored = restoreLabSnapshot(
                        userId, labName, career, userTeamId, message);
                    if (restored.isPresent()) {
                        return persistLabMutation(career, restored.get());
                    }
                }

                affected.forEach(player ->
                    applyLabStats(player, attack, defense, technique, speed, stamina, mentality));
                if (labKey.startsWith("prepare-")) {
                    Map<String, LineupSlotDTO> labSlots = new LinkedHashMap<>(slots);
                    for (int i = 0; i < affected.size(); i++) {
                        SessionPlayer player = affected.get(i);
                        ChannelSlot channelSlot = slotForChannel(channel, i);
                        labSlots.put(player.getSessionPlayerId(), new LineupSlotDTO(
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
                    .map(this::labPlayerDetails)
                    .toList());
                details.put("slotMutation", labKey.startsWith("prepare-")
                    ? "affected DEF forced into " + channel.name().toLowerCase() + " defensive channel"
                    : "fallback smoke defaults restored; exact snapshot unavailable");
                details.put("expectedScenarios", channel == DefenderChannel.CENTER
                    ? List.of("m45-opponent-central")
                    : List.of("m45-opponent-wide"));

                log.info("[V25D99.22.20-TESTHARNESS] {} userId={} team={} channel={} affected={}",
                    labKey, userId, userTeamId, channel,
                    affected.stream().map(SessionPlayer::getSessionPlayerId).toList());

                return persistLabMutation(career, new LabMutationResult(labKey, message, details));
            });
    }

    private List<SessionPlayer> findWideStartingDefenders(
            List<SessionPlayer> squad,
            Map<String, LineupSlotDTO> slots) {
        if (squad == null || squad.isEmpty() || slots == null || slots.isEmpty()) {
            return firstSquadDefenders(squad, 2);
        }
        Map<String, SessionPlayer> byId = squad.stream()
            .filter(p -> p != null && p.getSessionPlayerId() != null)
            .collect(Collectors.toMap(SessionPlayer::getSessionPlayerId, p -> p, (a, b) -> a));

        List<SessionPlayer> wide = slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .filter(slot -> {
                Double x = slotXPercent(slot);
                return x != null && (x < 35.0 || x > 65.0);
            })
            .map(slot -> byId.get(slot.playerId()))
            .filter(p -> p != null && "DEF".equals(p.getPosition()))
            .distinct()
            .toList();
        if (!wide.isEmpty()) {
            return wide;
        }
        return slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .map(slot -> byId.get(slot.playerId()))
            .filter(p -> p != null && "DEF".equals(p.getPosition()))
            .distinct()
            .limit(2)
            .toList();
    }

    private List<SessionPlayer> findStartingDefendersByChannel(
            List<SessionPlayer> squad,
            Map<String, LineupSlotDTO> slots,
            DefenderChannel channel,
            int limit) {
        if (limit <= 0) {
            return List.of();
        }
        if (squad == null || squad.isEmpty()) {
            return List.of();
        }
        if (slots == null || slots.isEmpty()) {
            return squadDefendersByChannelOrder(squad, channel, limit);
        }

        Map<String, SessionPlayer> byId = squad.stream()
            .filter(p -> p != null && p.getSessionPlayerId() != null)
            .collect(Collectors.toMap(SessionPlayer::getSessionPlayerId, p -> p, (a, b) -> a));

        List<LineupSlotDTO> allStartingSlots = slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .filter(slot -> byId.get(slot.playerId()) != null)
            .toList();
        if (allStartingSlots.size() < 2 && channel != DefenderChannel.CENTER) {
            return squadDefendersByChannelOrder(squad, channel, limit);
        }

        List<SessionPlayer> channelPlayers = slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .filter(slot -> {
                Double x = slotXPercent(slot);
                return x != null && channel.matches(x);
            })
            .map(slot -> byId.get(slot.playerId()))
            .filter(p -> p != null && "DEF".equals(p.getPosition()))
            .distinct()
            .limit(limit)
            .toList();
        if (!channelPlayers.isEmpty()) {
            return channelPlayers;
        }

        List<SessionPlayer> tacticalChannelPlayers = slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .filter(slot -> {
                Double x = slotXPercent(slot);
                return x != null && channel.matches(x);
            })
            .map(slot -> byId.get(slot.playerId()))
            .filter(p -> p != null)
            .distinct()
            .limit(limit)
            .toList();
        if (!tacticalChannelPlayers.isEmpty()) {
            return tacticalChannelPlayers;
        }

        List<LineupSlotDTO> defenderSlots = slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .filter(slot -> {
                SessionPlayer player = byId.get(slot.playerId());
                return player != null && "DEF".equals(player.getPosition());
            })
            .toList();

        List<LineupSlotDTO> nearestSlots = !defenderSlots.isEmpty()
            && (channel == DefenderChannel.CENTER || defenderSlots.size() >= 2)
            ? defenderSlots
            : allStartingSlots;
        if (nearestSlots.isEmpty()) {
            return squadDefendersByChannelOrder(squad, channel, limit);
        }

        return nearestSlots.stream()
            .sorted((a, b) -> {
                double ax = Optional.ofNullable(slotXPercent(a)).orElse(50.0);
                double bx = Optional.ofNullable(slotXPercent(b)).orElse(50.0);
                return switch (channel) {
                    case LEFT -> Double.compare(ax, bx);
                    case RIGHT -> Double.compare(bx, ax);
                    case CENTER -> Double.compare(Math.abs(ax - 50.0), Math.abs(bx - 50.0));
                };
            })
            .map(slot -> byId.get(slot.playerId()))
            .distinct()
            .limit(limit)
            .toList();
    }

    private ChannelSlot slotForChannel(DefenderChannel channel, int index) {
        return switch (channel) {
            case LEFT -> new ChannelSlot("S22-1", 18.0);
            case RIGHT -> new ChannelSlot("S24-3", 82.0);
            case CENTER -> index % 2 == 0
                ? new ChannelSlot("S23-1", 42.0)
                : new ChannelSlot("S23-3", 58.0);
        };
    }

    private List<SessionPlayer> firstSquadDefenders(List<SessionPlayer> squad, int limit) {
        if (squad == null || squad.isEmpty()) {
            return List.of();
        }
        return squad.stream()
            .filter(p -> p != null
                && p.getSessionPlayerId() != null
                && "DEF".equals(p.getPosition()))
            .limit(limit)
            .toList();
    }

    private List<SessionPlayer> squadDefendersByChannelOrder(
            List<SessionPlayer> squad,
            DefenderChannel channel,
            int limit) {
        if (squad == null || squad.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<SessionPlayer> defenders = squad.stream()
            .filter(p -> p != null
                && p.getSessionPlayerId() != null
                && "DEF".equals(p.getPosition()))
            .toList();
        List<SessionPlayer> sideCandidates = defenders.size() >= Math.max(2, limit)
            ? defenders
            : squad.stream()
                .filter(p -> p != null && p.getSessionPlayerId() != null)
                .filter(p -> !"GK".equals(p.getPosition()))
                .toList();
        if (channel == DefenderChannel.RIGHT) {
            return IntStream.range(0, sideCandidates.size())
                .mapToObj(i -> sideCandidates.get(sideCandidates.size() - 1 - i))
                .limit(limit)
                .toList();
        }
        return sideCandidates.stream()
            .limit(limit)
            .toList();
    }

    private Double slotXPercent(LineupSlotDTO slot) {
        if (slot == null) return null;
        if (slot.customXPercent() != null && Double.isFinite(slot.customXPercent())) {
            return slot.customXPercent();
        }
        int[] parsed = parseSlotSubdivision(slot);
        if (parsed == null) return null;
        int sector = parsed[0];
        int subIndex = parsed[1];
        int sectorCol = (sector - 1) % 3;
        double left = (sectorCol * 3 + (subIndex - 1)) * 11.11;
        return Math.max(0.0, Math.min(100.0, left + 11.11 / 2.0));
    }

    private int[] parseSlotSubdivision(LineupSlotDTO slot) {
        if (slot == null || slot.subdivisionId() == null || !slot.subdivisionId().startsWith("S")) {
            return null;
        }
        String id = slot.subdivisionId();
        int dash = id.indexOf('-');
        if (dash < 0 || dash >= id.length() - 1) return null;
        try {
            int sector = Integer.parseInt(id.substring(1, dash));
            int subIndex = Integer.parseInt(id.substring(dash + 1));
            if (sector < 1 || sector > 27 || subIndex < 1 || subIndex > 3) return null;
            return new int[] { sector, subIndex };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private SessionPlayer findSquadPlayerByName(List<SessionPlayer> squad, String name) {
        if (squad == null || name == null) {
            return null;
        }
        return squad.stream()
            .filter(p -> p != null && name.equalsIgnoreCase(p.getName()))
            .findFirst()
            .orElse(null);
    }

    private void applyLabStats(SessionPlayer player,
                               int attack,
                               int defense,
                               int technique,
                               int speed,
                               int stamina,
                               int mentality) {
        player.setAttack(attack);
        player.setDefense(defense);
        player.setTechnique(technique);
        player.setSpeed(speed);
        player.setStamina(stamina);
        player.setMentality(mentality);
    }

    private void rememberLabSnapshot(UUID userId,
                                     String labName,
                                     CareerSave career,
                                     String teamId,
                                     List<SessionPlayer> affectedPlayers) {
        String key = labSnapshotKey(userId, labName);
        labSnapshots.computeIfAbsent(key, ignored -> {
            Map<String, PlayerStatSnapshot> playerStats = new LinkedHashMap<>();
            if (affectedPlayers != null) {
                for (SessionPlayer player : affectedPlayers) {
                    if (player != null && player.getSessionPlayerId() != null) {
                        playerStats.put(player.getSessionPlayerId(), PlayerStatSnapshot.from(player));
                    }
                }
            }
            Map<String, LineupSlotDTO> slots = new LinkedHashMap<>(
                career.getTeamStarting11SubdivisionSlots().getOrDefault(teamId, Map.of()));
            return new LabSnapshot(teamId, playerStats, slots);
        });
    }

    private Optional<LabMutationResult> restoreLabSnapshot(UUID userId,
                                                           String labName,
                                                           CareerSave career,
                                                           String fallbackTeamId,
                                                           String message) {
        LabSnapshot snapshot = labSnapshots.remove(labSnapshotKey(userId, labName));
        if (snapshot == null) {
            return Optional.empty();
        }

        String teamId = snapshot.teamId() != null ? snapshot.teamId() : fallbackTeamId;
        List<SessionPlayer> squad = career.getTeamSquad(teamId);
        Map<String, SessionPlayer> byId = squad.stream()
            .filter(p -> p != null && p.getSessionPlayerId() != null)
            .collect(Collectors.toMap(SessionPlayer::getSessionPlayerId, p -> p, (a, b) -> a));

        List<Map<String, Object>> restoredPlayers = new ArrayList<>();
        for (Map.Entry<String, PlayerStatSnapshot> entry : snapshot.playerStats().entrySet()) {
            SessionPlayer player = byId.get(entry.getKey());
            if (player == null) {
                continue;
            }
            entry.getValue().applyTo(player);
            restoredPlayers.add(labPlayerDetails(player));
        }
        career.replaceTeamStarting11SubdivisionRaw(teamId, snapshot.slots());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("userTeamId", teamId);
        details.put("restoredFromSnapshot", true);
        details.put("restoredPlayers", restoredPlayers);
        details.put("restoredSlotCount", snapshot.slots().size());
        details.put("message", "Exact in-memory lab snapshot restored");

        return Optional.of(new LabMutationResult(
            "restore-" + labName + "-lab",
            message + " (exact snapshot)",
            details));
    }

    private Mono<LabMutationResult> persistLabMutation(CareerSave career, LabMutationResult result) {
        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())))
            .thenReturn(result);
    }

    private String labSnapshotKey(UUID userId, String labName) {
        return userId + ":" + labName;
    }

    private Map<String, Object> labPlayerDetails(SessionPlayer player) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("playerId", player.getSessionPlayerId());
        details.put("name", player.getName());
        details.put("position", player.getPosition());
        details.put("attack", player.getAttack());
        details.put("defense", player.getDefense());
        details.put("technique", player.getTechnique());
        details.put("speed", player.getSpeed());
        details.put("stamina", player.getStamina());
        details.put("mentality", player.getMentality());
        details.put("score", substitutionScore(player));
        return details;
    }

    private record LabSnapshot(
        String teamId,
        Map<String, PlayerStatSnapshot> playerStats,
        Map<String, LineupSlotDTO> slots
    ) {}

    private record PlayerStatSnapshot(
        Integer attack,
        Integer defense,
        Integer technique,
        Integer speed,
        Integer stamina,
        Integer mentality
    ) {
        static PlayerStatSnapshot from(SessionPlayer player) {
            return new PlayerStatSnapshot(
                player.getAttack(),
                player.getDefense(),
                player.getTechnique(),
                player.getSpeed(),
                player.getStamina(),
                player.getMentality());
        }

        void applyTo(SessionPlayer player) {
            player.setAttack(attack);
            player.setDefense(defense);
            player.setTechnique(technique);
            player.setSpeed(speed);
            player.setStamina(stamina);
            player.setMentality(mentality);
        }
    }

    private enum DefenderChannel {
        LEFT {
            @Override
            boolean matches(double x) {
                return x < 35.0;
            }
        },
        CENTER {
            @Override
            boolean matches(double x) {
                return x >= 35.0 && x <= 65.0;
            }
        },
        RIGHT {
            @Override
            boolean matches(double x) {
                return x > 65.0;
            }
        };

        abstract boolean matches(double x);
    }

    private record ChannelSlot(String subdivisionId, double x) {}

    // ========== createCustom ==========


    @Override
    public Mono<CareerSave> createCustom(UUID userId, String worldLeagueId, String worldTeamId,
                                          String difficulty, String gameSpeed, int teamsPerDivision) {
        if (teamsPerDivision < 2) {
            return Mono.error(new IllegalArgumentException(
                "teamsPerDivision must be >= 2 (got " + teamsPerDivision + ")"));
        }

        log.info("[V24D20-TESTHARNESS] createCustom userId={} league={} team={} "
                + "difficulty={} gameSpeed={} teamsPerDivision={}",
            userId, worldLeagueId, worldTeamId, difficulty, gameSpeed, teamsPerDivision);

        return careerSessionService.deleteCareer(userId)
            .then(careerSessionService.startNewCareer(
                userId, worldLeagueId, worldTeamId, difficulty, gameSpeed, teamsPerDivision))
            .flatMap(career -> careerRepository.findById(userId.toString())
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Mono.just(career);
                    }
                    return executeResetInjuries(opt.get())
                        .thenReturn(opt.get());
                }));
    }

    // ========== snapshot ==========

    @Override
    public Mono<CareerSave> snapshot(UUID userId) {
        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                return Mono.just(optionalCareer.get());
            });
    }

    // ========== replayMatch (V24D20-SANDBOX-V2-MVP F5) ==========

    @Override
    public Mono<MatchFixture> replayMatch(UUID userId, String matchId, Long seedOverride) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        long seed = (seedOverride != null) ? seedOverride : System.currentTimeMillis();

        log.trace("[V24D20-SANDBOX-V2-MVP] replayMatch userId={}, matchId={}, seed={}",
            userId, matchId, seed);

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return executeReplayMatch(career, matchId, seed);
            });
    }

    @Override
    public Mono<MatchPreviewSummary> runMatchPreviewSummary(
            UUID userId,
            String matchId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        int safeSeedCount = Math.max(1, Math.min(50, seedCount));
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                MatchFixture fixture = career.getTournamentState().getFixtures().stream()
                    .filter(f -> f.getMatchId().equals(matchId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Match not found in current tournament: " + matchId));
                SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
                SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
                if (home == null || away == null) {
                    return Mono.error(new IllegalStateException(
                        "SessionTeam not found for match " + matchId
                        + " (home=" + fixture.getHomeTeamId()
                        + ", away=" + fixture.getAwayTeamId() + ")"));
                }

                boolean userIsHome = previewControlledTeamIsHome(career, fixture, controlledTeamSide);
                PreviewSums sums = new PreviewSums();
                for (int i = 0; i < safeSeedCount; i++) {
                    long seed = seedStart + i;
                    V24MatchContext context = v24ContextFactory.build(career, fixture, home, away, seed);
                    V24DetailedMatchResult result = new V24DetailedMatchEngine()
                        .simulate(context, new Random(seed));
                    addPreviewSample(sums, result, userIsHome);
                }

                SessionTeam controlledTeam = userIsHome ? home : away;
                String side = userIsHome ? "HOME" : "AWAY";
                int n = safeSeedCount;
                return Mono.just(new MatchPreviewSummary(
                    matchId,
                    side,
                    seedStart,
                    seedStart + safeSeedCount - 1L,
                    safeSeedCount,
                    controlledTeam.getName(),
                    controlledTeam.getFormation(),
                    round2(sums.goalsFor / n),
                    round2(sums.goalsAgainst / n),
                    round2((sums.goalsFor - sums.goalsAgainst) / n),
                    round2(sums.possessionFor / n),
                    round2(sums.shotsFor / n),
                    round2(sums.shotsAgainst / n),
                    round2((sums.shotsFor - sums.shotsAgainst) / n),
                    round3(sums.xgFor / n),
                    round3(sums.xgAgainst / n),
                    round3((sums.xgFor - sums.xgAgainst) / n),
                    round2(sums.centralShotsFor / n),
                    round2(sums.wideShotsFor / n),
                    round2(sums.longShotsFor / n),
                    round2(sums.centralShotsAgainst / n),
                    round2(sums.wideShotsAgainst / n),
                    round2(sums.longShotsAgainst / n)
                ));
            });
    }

    private boolean previewControlledTeamIsHome(
            CareerSave career,
            MatchFixture fixture,
            String controlledTeamSide) {
        String side = controlledTeamSide == null ? "USER" : controlledTeamSide.trim().toUpperCase(Locale.ROOT);
        if ("HOME".equals(side)) return true;
        if ("AWAY".equals(side)) return false;
        String userTeamId = career.getUserSessionTeamId();
        return Objects.equals(fixture.getHomeTeamId(), userTeamId);
    }

    private void addPreviewSample(PreviewSums sums, V24DetailedMatchResult result, boolean userIsHome) {
        sums.goalsFor += userIsHome ? result.homeGoals() : result.awayGoals();
        sums.goalsAgainst += userIsHome ? result.awayGoals() : result.homeGoals();
        sums.possessionFor += userIsHome ? result.homePossession() : result.awayPossession();
        sums.shotsFor += userIsHome ? result.homeShots() : result.awayShots();
        sums.shotsAgainst += userIsHome ? result.awayShots() : result.homeShots();
        sums.xgFor += userIsHome ? result.homeXg() : result.awayXg();
        sums.xgAgainst += userIsHome ? result.awayXg() : result.homeXg();

        String ownTeamId = userIsHome ? result.homeTeamId() : result.awayTeamId();
        if (result.timeline() == null || result.timeline().events() == null) {
            return;
        }
        for (V24MatchEvent event : result.timeline().events()) {
            if (!previewIsShotLike(event)) continue;
            boolean ownShot = Objects.equals(event.teamId(), ownTeamId);
            V24ShotLocation location = event.shotCoordinate() != null
                ? event.shotCoordinate().location()
                : null;
            if (location == V24ShotLocation.PENALTY_AREA_WIDE) {
                if (ownShot) sums.wideShotsFor += 1.0; else sums.wideShotsAgainst += 1.0;
            } else if (location == V24ShotLocation.OUTSIDE_BOX || location == V24ShotLocation.LONG_RANGE) {
                if (ownShot) sums.longShotsFor += 1.0; else sums.longShotsAgainst += 1.0;
            } else {
                if (ownShot) sums.centralShotsFor += 1.0; else sums.centralShotsAgainst += 1.0;
            }
        }
    }

    private boolean previewIsShotLike(V24MatchEvent event) {
        if (event == null || event.xg() <= 0.0) return false;
        return event.type() == V24MatchEventType.SHOT
            || event.type() == V24MatchEventType.SHOT_ON_TARGET
            || event.type() == V24MatchEventType.MISS
            || event.type() == V24MatchEventType.BLOCK
            || event.type() == V24MatchEventType.GOAL;
    }

    private static final class PreviewSums {
        double goalsFor;
        double goalsAgainst;
        double possessionFor;
        double shotsFor;
        double shotsAgainst;
        double xgFor;
        double xgAgainst;
        double centralShotsFor;
        double wideShotsFor;
        double longShotsFor;
        double centralShotsAgainst;
        double wideShotsAgainst;
        double longShotsAgainst;
    }

    @Override
    public Mono<LineupDiagnostic> lineupDiagnostic(UUID userId, String matchId, Long seedOverride) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        long seed = (seedOverride != null) ? seedOverride : 12345L;
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                MatchFixture fixture = career.getTournamentState().getFixtures().stream()
                    .filter(f -> f.getMatchId().equals(matchId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Match not found in current tournament: " + matchId));
                SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
                SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
                if (home == null || away == null) {
                    return Mono.error(new IllegalStateException(
                        "SessionTeam not found for match " + matchId
                        + " (home=" + fixture.getHomeTeamId()
                        + ", away=" + fixture.getAwayTeamId() + ")"));
                }
                V24MatchContext context = v24ContextFactory.build(career, fixture, home, away, seed);
                return Mono.just(new LineupDiagnostic(
                    matchId,
                    seed,
                    buildLineupDiagnosticTeam(
                        context.homeTeamId(),
                        context.homeTeam().getName(),
                        context.homeFormation(),
                        context.homeStyle(),
                        context.homeStartingPlayers(),
                        context.homeSlotsByPlayerId()),
                    buildLineupDiagnosticTeam(
                        context.awayTeamId(),
                        context.awayTeam().getName(),
                        context.awayFormation(),
                        context.awayStyle(),
                        context.awayStartingPlayers(),
                        context.awaySlotsByPlayerId())
                ));
            });
    }

    private LineupDiagnosticTeam buildLineupDiagnosticTeam(
            String teamId,
            String teamName,
            String formation,
            TeamStyle style,
            List<SessionPlayer> starters,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        List<LineupDiagnosticPlayer> players = starters.stream()
            .map(player -> buildLineupDiagnosticPlayer(
                player,
                resolveDiagnosticSlot(player, formation, starters, slotsByPlayerId)))
            .toList();
        double avgOverall = players.stream()
            .mapToInt(LineupDiagnosticPlayer::overall)
            .average()
            .orElse(0.0);
        double avgCollective = players.stream()
            .mapToDouble(LineupDiagnosticPlayer::collective)
            .average()
            .orElse(0.0);
        double avgEffectiveness = players.stream()
            .mapToDouble(LineupDiagnosticPlayer::effectiveness)
            .average()
            .orElse(0.0);
        return new LineupDiagnosticTeam(
            teamId,
            teamName,
            formation,
            style,
            round2(avgOverall),
            round2(avgCollective),
            round3(avgEffectiveness),
            players.size(),
            buildLineupWidthDiagnostic(players),
            players
        );
    }

    private LineupWidthDiagnostic buildLineupWidthDiagnostic(List<LineupDiagnosticPlayer> players) {
        List<LineupDiagnosticPlayer> outfield = players == null
            ? List.of()
            : players.stream()
                .filter(Objects::nonNull)
                .filter(player -> !"GK".equalsIgnoreCase(player.tacticalPosition()))
                .toList();
        int leftCount = 0;
        int centerCount = 0;
        int rightCount = 0;
        double leftXSum = 0.0;
        double rightXSum = 0.0;
        for (LineupDiagnosticPlayer player : outfield) {
            String side = diagnosticPlayerLane(player);
            if ("LEFT".equals(side)) {
                leftCount++;
                leftXSum += player.xPercent() != null ? player.xPercent() : 25.0;
            } else if ("RIGHT".equals(side)) {
                rightCount++;
                rightXSum += player.xPercent() != null ? player.xPercent() : 75.0;
            } else {
                centerCount++;
            }
        }
        int wideCount = leftCount + rightCount;
        double leftAvgX = leftCount > 0 ? round2(leftXSum / leftCount) : 0.0;
        double rightAvgX = rightCount > 0 ? round2(rightXSum / rightCount) : 0.0;
        double widthScore = outfield.isEmpty() ? 0.0 : round2((wideCount * 100.0) / outfield.size());
        double sideBalance = wideCount == 0 ? 0.0 : round2(100.0 - (Math.abs(leftCount - rightCount) * 100.0 / wideCount));
        String verdict;
        if (wideCount < 2) {
            verdict = "Revisar ancho";
        } else if (sideBalance < 45.0) {
            verdict = "Revisar lado";
        } else if (widthScore < 35.0) {
            verdict = "Estrecha";
        } else if (sideBalance < 70.0) {
            verdict = "Parcial";
        } else {
            verdict = "OK";
        }
        return new LineupWidthDiagnostic(
            leftCount,
            centerCount,
            rightCount,
            wideCount,
            leftAvgX,
            rightAvgX,
            widthScore,
            sideBalance,
            verdict,
            lineupWidthRead(leftCount, centerCount, rightCount, widthScore, sideBalance, verdict)
        );
    }

    private String diagnosticPlayerLane(LineupDiagnosticPlayer player) {
        String roleSide = player.slotSide();
        if ("LEFT".equals(roleSide) || "RIGHT".equals(roleSide)) {
            return roleSide;
        }
        Double x = player.xPercent();
        if (x != null && Double.isFinite(x)) {
            if (x <= 42.0) return "LEFT";
            if (x >= 58.0) return "RIGHT";
        }
        return "CENTER";
    }

    private String lineupWidthRead(
            int leftCount,
            int centerCount,
            int rightCount,
            double widthScore,
            double sideBalance,
            String verdict) {
        String base = "Carriles: izquierda " + leftCount
            + ", centro " + centerCount
            + ", derecha " + rightCount
            + ". Ancho " + widthScore + "%, balance lateral " + sideBalance + "%.";
        return switch (verdict) {
            case "OK" -> base + " La estructura ofrece salida por ambos lados.";
            case "Parcial" -> base + " Hay banda, pero un lado queda mas cargado que el otro.";
            case "Estrecha" -> base + " La formacion concentra demasiados jugadores por dentro.";
            case "Revisar lado" -> base + " Un carril queda claramente mas poblado; revisar roles o movimientos.";
            default -> base + " Falta presencia real de banda; puede explicar espejos laterales pobres.";
        };
    }

    private LineupDiagnosticPlayer buildLineupDiagnosticPlayer(
            SessionPlayer player,
            ResolvedDiagnosticSlot slot) {
        String natural = safePosition(player.getPosition());
        String tactical = tacticalPositionForDiagnostic(slot, natural);
        String slotRole = slot != null && slot.role() != null ? slot.role() : tactical;
        String slotSide = diagnosticSlotSide(slot);
        CuratedMatrixRoleProfile profile = curatedMatrixRoleProfile(player);
        int roleBonus = diagnosticRoleBonus(profile, slotRole);
        int sideBonus = diagnosticSideBonus(profile, slotSide);
        int assignmentScore = formationPositionFitScore(player, diagnosticFormationPosition(slot));
        String assignmentVerdict = assignmentVerdict(natural, tactical, roleBonus, sideBonus, assignmentScore);
        String assignmentRead = assignmentRead(player, natural, slotRole, slotSide, profile, assignmentVerdict, roleBonus, sideBonus);
        double effectiveness = slot != null
            && slot.xPercent() != null && Double.isFinite(slot.xPercent())
            && slot.yPercent() != null && Double.isFinite(slot.yPercent())
            ? com.footballmanager.domain.model.valueobject.SubdivisionEffectivenessCalculator
                .effectiveness(natural, slot.xPercent(), slot.yPercent(), tactical)
            : PositionEffectivenessCalculator.effectiveness(natural, tactical);
        int attack = intOr(player.getAttack(), 50);
        int defense = intOr(player.getDefense(), 50);
        int technique = intOr(player.getTechnique(), 50);
        int speed = intOr(player.getSpeed(), 50);
        int stamina = intOr(player.getStamina(), 50);
        int mentality = intOr(player.getMentality(), 50);
        int overall = (int) Math.round((attack + defense + technique + speed + stamina + mentality) / 6.0);
        double baseCollective = "GK".equals(natural)
            ? ((defense + mentality) / 2.0)
            : ((attack + defense + mentality) / 3.0);
        return new LineupDiagnosticPlayer(
            player.getSessionPlayerId(),
            player.getName(),
            natural,
            tactical,
            slotRole,
            slotSide,
            slot != null ? slot.subdivisionId() : null,
            slot != null ? finiteOrNull(slot.xPercent()) : null,
            slot != null ? finiteOrNull(slot.yPercent()) : null,
            slot != null ? slot.source() : "missing",
            profile != null ? String.join(" · ", profile.roles()) : "-",
            profile != null ? String.join(" · ", profile.sides()) : "-",
            roleBonus,
            sideBonus,
            assignmentScore,
            assignmentVerdict,
            assignmentRead,
            attack,
            defense,
            technique,
            speed,
            stamina,
            mentality,
            overall,
            round3(effectiveness),
            round2(baseCollective * effectiveness)
        );
    }

    /**
     * V25D99.174: XI efectivo must be a true pitch diagnostic, not just a player
     * list. The match engine can receive either persisted/manual slots or a
     * default formation with no saved slots. For the latter, derive the
     * canonical slot from FormationService so the debug UI can show the same
     * base coordinates the manager sees in the modal.
     */
    private ResolvedDiagnosticSlot resolveDiagnosticSlot(
            SessionPlayer player,
            String formation,
            List<SessionPlayer> starters,
            Map<String, LineupSlotDTO> slotsByPlayerId) {
        LineupSlotDTO manual = slotsByPlayerId != null ? slotsByPlayerId.get(player.getSessionPlayerId()) : null;
        FormationPositionDTO canonical = null;
        if (manual != null && manual.subdivisionId() != null && !manual.subdivisionId().isBlank()) {
            canonical = findFormationPosition(formation, manual.subdivisionId());
        }
        if (canonical == null) {
            canonical = canonicalPositionByStarterIndex(formation, starters, player);
        }
        if (manual == null && canonical == null) {
            return null;
        }
        String subdivisionId = manual != null && manual.subdivisionId() != null && !manual.subdivisionId().isBlank()
            ? manual.subdivisionId()
            : canonical != null ? canonical.subdivisionId() : null;
        boolean hasCustomX = manual != null && finiteOrNull(manual.customXPercent()) != null;
        boolean hasCustomY = manual != null && finiteOrNull(manual.customYPercent()) != null;
        Double xPercent = hasCustomX
            ? manual.customXPercent()
            : canonical != null ? canonical.xPercent() : null;
        Double yPercent = hasCustomY
            ? manual.customYPercent()
            : canonical != null ? canonical.yPercent() : null;
        String source = (hasCustomX || hasCustomY)
            ? "modal-custom"
            : manual != null ? "persisted-slot" : "canonical";
        return new ResolvedDiagnosticSlot(
            subdivisionId,
            canonical != null ? canonical.role() : null,
            xPercent,
            yPercent,
            source);
    }

    private FormationPositionDTO canonicalPositionByStarterIndex(
            String formation,
            List<SessionPlayer> starters,
            SessionPlayer player) {
        if (formation == null || formation.isBlank() || starters == null || starters.isEmpty() || player == null) {
            return null;
        }
        List<FormationPositionDTO> positions = formationPositions(formation);
        if (positions.isEmpty()) return null;
        int index = -1;
        for (int i = 0; i < starters.size(); i++) {
            SessionPlayer starter = starters.get(i);
            if (starter != null && Objects.equals(starter.getSessionPlayerId(), player.getSessionPlayerId())) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= positions.size()) return null;
        return positions.get(index);
    }

    private FormationPositionDTO findFormationPosition(String formation, String subdivisionId) {
        if (formation == null || formation.isBlank() || subdivisionId == null || subdivisionId.isBlank()) {
            return null;
        }
        return formationPositions(formation).stream()
            .filter(position -> subdivisionId.equals(position.subdivisionId()))
            .findFirst()
            .orElse(null);
    }

    private List<FormationPositionDTO> formationPositions(String formation) {
        try {
            FormationDTO dto = formationService.getFormationByName(formation);
            if (dto == null || dto.positions() == null) return List.of();
            return dto.positions().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                    FormationPositionDTO::index,
                    Comparator.nullsLast(Integer::compareTo)))
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String tacticalPositionForDiagnostic(ResolvedDiagnosticSlot slot, String naturalPosition) {
        if (slot == null) return naturalPosition;
        if ("GK-1".equals(slot.subdivisionId()) || "GK".equalsIgnoreCase(naturalPosition)) {
            return "GK";
        }
        Double customY = slot.yPercent();
        if (customY != null && Double.isFinite(customY)) {
            double y = Math.max(0.0, Math.min(100.0, customY));
            String naturalLine = tacticalLineForNaturalPosition(naturalPosition);
            if (isNear(y, 34.0, 2.0)) {
                if ("ATT".equals(naturalLine) || "MID".equals(naturalLine)) {
                    return naturalLine;
                }
            }
            if (isNear(y, 67.0, 2.0)) {
                if ("MID".equals(naturalLine) || "DEF".equals(naturalLine)) {
                    return naturalLine;
                }
            }
            if (y < 34.0) return "ATT";
            if (y < 67.0) return "MID";
            return "DEF";
        }
        String category = com.footballmanager.domain.model.valueobject.FormationInferer.categoryFor(slot.subdivisionId());
        return (category == null || category.isBlank()) ? naturalPosition : category;
    }

    private boolean isNear(double value, double pivot, double radius) {
        return Math.abs(value - pivot) <= radius;
    }

    private String tacticalLineForNaturalPosition(String naturalPosition) {
        if (naturalPosition == null || naturalPosition.isBlank()) {
            return "";
        }
        return switch (naturalPosition.toUpperCase(Locale.ROOT)) {
            case "GK" -> "GK";
            case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> "DEF";
            case "MID", "CM", "CDM", "DM", "CAM", "AM", "LM", "RM" -> "MID";
            case "ATT", "ST", "CF", "LW", "RW", "WINGER" -> "ATT";
            default -> "";
        };
    }

    private String diagnosticRoleLine(String role) {
        if (role == null || role.isBlank()) return "";
        return switch (role.toUpperCase(Locale.ROOT)) {
            case "GK" -> "GK";
            case "LB", "CB", "RB", "LWB", "RWB" -> "DEF";
            case "CDM", "CM", "CAM", "LM", "RM" -> "MID";
            case "LW", "RW", "CF", "ST" -> "ATT";
            default -> "";
        };
    }

    private record ResolvedDiagnosticSlot(
        String subdivisionId,
        String role,
        Double xPercent,
        Double yPercent,
        String source
    ) {}

    private FormationPositionDTO diagnosticFormationPosition(ResolvedDiagnosticSlot slot) {
        if (slot == null) {
            return new FormationPositionDTO(null, null, null, null, null, null);
        }
        return new FormationPositionDTO(
            null,
            slot.role(),
            slot.xPercent(),
            slot.yPercent(),
            null,
            slot.subdivisionId());
    }

    private String diagnosticSlotSide(ResolvedDiagnosticSlot slot) {
        if (slot == null) return "UNKNOWN";
        return matrixSlotSide(diagnosticFormationPosition(slot));
    }

    private int diagnosticRoleBonus(CuratedMatrixRoleProfile profile, String slotRole) {
        if (profile == null || slotRole == null || slotRole.isBlank()) return 0;
        String role = slotRole.toUpperCase(Locale.ROOT);
        if (profile.roles().contains(role)) return 26;
        if (curatedMatrixRoleFamilyMatch(profile.roles(), role)) return 12;
        return 0;
    }

    private int diagnosticSideBonus(CuratedMatrixRoleProfile profile, String slotSide) {
        if (profile == null || slotSide == null) return 0;
        if ("LEFT".equals(slotSide) || "RIGHT".equals(slotSide)) {
            if (profile.sides().contains(slotSide) || profile.sides().contains("BOTH")) return 22;
            if (profile.sides().contains(matrixOppositeSide(slotSide))) return -34;
        }
        if ("CENTER".equals(slotSide) && profile.sides().contains("CENTER")) return 8;
        return 0;
    }

    private String assignmentVerdict(String natural, String tactical, int roleBonus, int sideBonus, int assignmentScore) {
        if ("GK".equals(natural)) return "OK";
        if (sideBonus < 0) return "Revisar lado";
        if (assignmentScore < 70) return "Revisar rol";
        if (roleBonus > 0 || sideBonus > 0 || Objects.equals(natural, tactical)) return "OK";
        return "Aceptable";
    }

    private String assignmentRead(
            SessionPlayer player,
            String natural,
            String slotRole,
            String slotSide,
            CuratedMatrixRoleProfile profile,
            String verdict,
            int roleBonus,
            int sideBonus) {
        String name = player != null ? player.getName() : "Jugador";
        if (isWingbackFallback(slotRole, natural)) {
            return name + " queda en " + slotRole
                + " como fallback de carrilero: faltan perfiles naturales compatibles "
                + compatibleWingbackProfiles(slotRole)
                + ". Es jugable, pero debe penalizarse y leerse como alerta tactica.";
        }
        if (isDefensiveLineFallback(slotRole, natural)) {
            return name + " queda en " + slotRole
                + " como fallback defensivo: faltan perfiles naturales compatibles "
                + compatibleDefensiveProfiles(slotRole)
                + ". Puede sostener la formacion, pero expone duelos y coberturas.";
        }
        if (isAttackingLineFallback(slotRole, natural)) {
            return name + " queda en " + slotRole
                + " como fallback ofensivo: faltan perfiles naturales compatibles "
                + compatibleAttackingProfiles(slotRole)
                + ". Puede completar el once, pero debe afectar amenaza, desmarques y definicion.";
        }
        if ("Revisar lado".equals(verdict)) {
            return name + " queda en " + slotSide + " pero su perfil prefiere "
                + (profile != null ? String.join("/", profile.sides()) : "otro lado") + ".";
        }
        if ("Revisar rol".equals(verdict)) {
            return name + " queda en " + slotRole + " con bajo encaje para su perfil.";
        }
        if (roleBonus > 0 && sideBonus > 0) {
            return "Encaja por rol y lado.";
        }
        if (roleBonus > 0) {
            return "Encaja por rol; lado neutro o no curado.";
        }
        if (sideBonus > 0) {
            return "Encaja por lado; rol aceptable por familia/categoria.";
        }
        return "Asignacion aceptable sin perfil curado fuerte.";
    }

    private boolean isWingbackFallback(String slotRole, String natural) {
        if (slotRole == null || natural == null) return false;
        String role = slotRole.toUpperCase(Locale.ROOT);
        String playerPosition = natural.toUpperCase(Locale.ROOT);
        if ("LWB".equals(role)) {
            return !Set.of("LWB", "LB", "LM", "LW", "WINGER", "DEF").contains(playerPosition);
        }
        if ("RWB".equals(role)) {
            return !Set.of("RWB", "RB", "RM", "RW", "WINGER", "DEF").contains(playerPosition);
        }
        return false;
    }

    private String compatibleWingbackProfiles(String slotRole) {
        if (slotRole == null) return "(LWB/RWB/LB/RB/LM/RM/LW/RW/WINGER)";
        return switch (slotRole.toUpperCase(Locale.ROOT)) {
            case "LWB" -> "(LWB/LB/LM/LW/WINGER/DEF)";
            case "RWB" -> "(RWB/RB/RM/RW/WINGER/DEF)";
            default -> "(LWB/RWB/LB/RB/LM/RM/LW/RW/WINGER)";
        };
    }

    private boolean isDefensiveLineFallback(String slotRole, String natural) {
        if (slotRole == null || natural == null) return false;
        String role = slotRole.toUpperCase(Locale.ROOT);
        String playerPosition = natural.toUpperCase(Locale.ROOT);
        return switch (role) {
            case "CB" -> !Set.of("CB", "DEF", "CDM", "LB", "RB", "LWB", "RWB").contains(playerPosition);
            case "LB" -> !Set.of("LB", "LWB", "LM", "LW", "DEF", "CB").contains(playerPosition);
            case "RB" -> !Set.of("RB", "RWB", "RM", "RW", "DEF", "CB").contains(playerPosition);
            default -> false;
        };
    }

    private String compatibleDefensiveProfiles(String slotRole) {
        if (slotRole == null) return "(CB/LB/RB/LWB/RWB/DEF/CDM)";
        return switch (slotRole.toUpperCase(Locale.ROOT)) {
            case "CB" -> "(CB/DEF/CDM/LB/RB/LWB/RWB)";
            case "LB" -> "(LB/LWB/LM/LW/DEF/CB)";
            case "RB" -> "(RB/RWB/RM/RW/DEF/CB)";
            default -> "(CB/LB/RB/LWB/RWB/DEF/CDM)";
        };
    }

    private boolean isAttackingLineFallback(String slotRole, String natural) {
        if (slotRole == null || natural == null) return false;
        String role = slotRole.toUpperCase(Locale.ROOT);
        String playerPosition = natural.toUpperCase(Locale.ROOT);
        return switch (role) {
            case "ST", "CF" -> !Set.of("ST", "CF", "ATT", "CAM", "WINGER", "LW", "RW").contains(playerPosition);
            case "LW" -> !Set.of("LW", "LM", "WINGER", "ATT", "CF", "ST", "LWB").contains(playerPosition);
            case "RW" -> !Set.of("RW", "RM", "WINGER", "ATT", "CF", "ST", "RWB").contains(playerPosition);
            default -> false;
        };
    }

    private String compatibleAttackingProfiles(String slotRole) {
        if (slotRole == null) return "(ST/CF/ATT/CAM/LW/RW/WINGER)";
        return switch (slotRole.toUpperCase(Locale.ROOT)) {
            case "ST", "CF" -> "(ST/CF/ATT/CAM/WINGER/LW/RW)";
            case "LW" -> "(LW/LM/WINGER/ATT/CF/ST/LWB)";
            case "RW" -> "(RW/RM/WINGER/ATT/CF/ST/RWB)";
            default -> "(ST/CF/ATT/CAM/LW/RW/WINGER)";
        };
    }

    private String safePosition(String position) {
        return (position == null || position.isBlank()) ? "MID" : position;
    }

    private Integer intOr(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private Double finiteOrNull(Double value) {
        if (value != null && Double.isFinite(value)) return value;
        return null;
    }

    private Mono<MatchFixture> executeReplayMatch(CareerSave career, String matchId, long seed) {
        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));

        // 1. Reset the fixture to PENDING (was COMPLETED from the original
        // simulation). The new V24 simulation will set it back to COMPLETED
        // via fixture.complete() below.
        fixture.reset();

        // 2. Build the V24 context and re-simulate.
        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            return Mono.error(new IllegalStateException(
                "SessionTeam not found for match " + matchId
                + " (home=" + fixture.getHomeTeamId()
                + ", away=" + fixture.getAwayTeamId() + ")"));
        }

        V24MatchContext context = v24ContextFactory.build(career, fixture, home, away, seed);
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchResult result = engine.simulate(context, new Random(seed));

        // V25D99.277: replayMatch powers the professional harness flow
        // "change formation/player/pixels -> replay -> open Match Compare".
        // Match Compare needs two persisted pieces: the live detail (saved
        // below) and the baseline snapshot that can be re-simulated with the
        // same seed. Live matches create this baseline in RoundController; the
        // harness replay path must do the same or the compare button opens a
        // valid route that returns 404.
        try {
            String careerId = career.getData().getCareerId();
            BaselineState baseline = BaselineState.empty(careerId, seed, context);
            baselineStoragePort.save(careerId, baseline)
                .onErrorResume(e -> {
                    log.warn("[V25D99.277] replayMatch: failed to persist baseline "
                        + "for matchId={}, continuing (compare may 404): {}",
                        matchId, e.getMessage());
                    return Mono.empty();
                })
                .block();
            log.trace("[V25D99.277] replayMatch: persisted baseline for Match Compare "
                + "matchId={}, careerId={}, seed={}", matchId, careerId, seed);
        } catch (Exception e) {
            log.warn("[V25D99.277] replayMatch: failed to prepare Match Compare baseline "
                + "for matchId={}, continuing: {}", matchId, e.getMessage());
        }

        // 3. Update the fixture with the new result. V25D37-F4: the V24 engine
        // already computes possession / shots in V24DetailedMatchResult — the
        // previous implementation passed `0, 0, 0, 0` for those four fields
        // (an unfinished stub from V24D20-SANDBOX-V2-MVP that never got wired
        // up), so any replay of a match returned
        // {homePossession: 0, awayPossession: 0, homeShots: 0, awayShots: 0}
        // (BUG_REPLAY_POSSESSION_ZERO — reported in V25D37 sprint). Now we
        // forward the real values from the engine. xG lives on the V24 detail
        // endpoint and is not stored on the fixture's MatchResultData.
        MatchFixture.MatchResultData resultData = new MatchFixture.MatchResultData(
            result.homeGoals(), result.awayGoals(),
            result.homePossession(), result.awayPossession(),
            result.homeShots(), result.awayShots());
        fixture.complete(resultData);

        // 4. Update standings with the new result. This will double-count if
        // the original result was already applied (the standings were
        // updated when the match first finished). Known limitation of MVP
        // replay — the manager should reset-injuries + replace-fixtures to
        // get a clean state if standings correctness is required.
        career.getTournamentState().updateStandingsWithResult(fixture);

        // 5. Best-effort: clear the old V24 detail from Redis so the next
        // GET /detail returns the new result, not the old one.
        try {
            String careerId = career.getData().getCareerId();
            v24StoragePort.deleteByMatchId(careerId, matchId);
        } catch (Exception e) {
            log.warn("[V24D20-SANDBOX-V2-MVP] replayMatch: failed to clear old V24 detail "
                + "for matchId={}, continuing (replay is best-effort): {}",
                matchId, e.getMessage());
        }

        // V24D21-SANDBOX-V2-MVP-F7 (BUG_REPLAY_NO_PERSIST): persist the NEW
        // V24 detail built from the re-simulation result. Without this, the
        // existing deleteByMatchId above leaves Redis empty and the next
        // GET /api/v1/careers/{careerId}/matches/{matchId}/detail returns
        // 404 — blocking the "what-if" smoke (replay with a changed
        // formation needs the new timeline / shot map / xG to compare
        // against the original).
        //
        // Mirrors LeagueSimulator.persistV24Detail() — same factory call
        // (V24DetailedMatchData.fromResult) and same storage port.
        // Player ratings are passed empty: the assembler lives inside
        // LeagueSimulator and replay currently has no per-player rating
        // derivation. This is a known limitation; a follow-up sprint
        // should extract V24PlayerRatingsAssembler so replay can reuse it.
        // Best-effort: a Redis failure logs a warning but does NOT fail
        // the replay — the fixture result is still saved to MongoDB and
        // the manager can re-run replay once Redis recovers.
        try {
            String careerId = career.getData().getCareerId();
            Integer seasonNumber = career.getCurrentSeason();
            Integer round = fixture.getRound();
            String homeTeamName = home.getName() != null ? home.getName() : "";
            String awayTeamName = away.getName() != null ? away.getName() : "";
            // V24D24-F1.2: capture formations from the SessionTeam at replay
            // time so the persisted detail reflects what formation was active
            // when the replay ran. Falls back to null for "—" in UI.
            String homeFormation = home.getFormation();
            String awayFormation = away.getFormation();

            V24DetailedMatchData newDetail = V24DetailedMatchData.fromResult(
                careerId,
                seasonNumber,
                round,
                homeTeamName,
                awayTeamName,
                homeFormation,
                awayFormation,
                result,
                List.<V24PlayerMatchRatingDto>of(),
                lineupSnapshot(context.homeStartingPlayers()),
                lineupSnapshot(context.homeBenchPlayers()),
                lineupSnapshot(context.awayStartingPlayers()),
                lineupSnapshot(context.awayBenchPlayers())
            );

            v24StoragePort.save(careerId, newDetail);
            log.trace("[V24D21-SANDBOX-V2-MVP] replayMatch: persisted new V24 detail "
                + "for matchId={}, careerId={}, homeGoals={}, awayGoals={}",
                matchId, careerId, result.homeGoals(), result.awayGoals());
        } catch (Exception e) {
            log.warn("[V24D21-SANDBOX-V2-MVP] replayMatch: failed to persist new V24 "
                + "detail for matchId={}, continuing (replay is best-effort): {}",
                matchId, e.getMessage());
        }

        log.trace("[V24D20-SANDBOX-V2-MVP] replayMatch complete: matchId={}, "
            + "newResult=({}-{}), seed={}",
            matchId, result.homeGoals(), result.awayGoals(), seed);

        // 6. Persist + invalidate cache (same pattern as the other endpoints)
        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())))
            .thenReturn(fixture);
    }

    @Override
    public Mono<List<FormationMatrixRow>> runFormationMatrix(UUID userId, String matchId, Long seedOverride, String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        long seed = (seedOverride != null) ? seedOverride : 12345L;

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                return Mono.fromSupplier(() -> executeFormationMatrix(optionalCareer.get(), matchId, seed, controlledTeamSide));
            });
    }

    private List<FormationMatrixRow> executeFormationMatrix(CareerSave career, String matchId, long seed, String controlledTeamSide) {
        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));

        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalStateException(
                "SessionTeam not found for match " + matchId
                    + " (home=" + fixture.getHomeTeamId()
                    + ", away=" + fixture.getAwayTeamId() + ")");
        }

        String controlledTeamId = resolveControlledTeamId(career, fixture, controlledTeamSide);
        boolean controlledIsHome = fixture.getHomeTeamId().equals(controlledTeamId);
        boolean controlledIsAway = fixture.getAwayTeamId().equals(controlledTeamId);
        if (!controlledIsHome && !controlledIsAway) {
            throw new IllegalArgumentException(
                "Formation matrix controlled team is not part of match: " + controlledTeamId);
        }

        TeamStyle homeStyle = home.getStyle() != null ? home.getStyle() : TeamStyle.BALANCED;
        TeamStyle awayStyle = away.getStyle() != null ? away.getStyle() : TeamStyle.BALANCED;

        V24MatchContext baseContext = v24ContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            homeStyle,
            awayStyle,
            seed);
        List<SessionPlayer> userStarters = controlledIsHome
            ? baseContext.homeStartingPlayers()
            : baseContext.awayStartingPlayers();
        List<SessionPlayer> userBench = controlledIsHome
            ? baseContext.homeBenchPlayers()
            : baseContext.awayBenchPlayers();
        if (userStarters.size() != 11) {
            throw new IllegalStateException(
                "Formation matrix needs exactly 11 user starters, got " + userStarters.size());
        }

        List<FormationMatrixRow> rows = new ArrayList<>();
        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        for (FormationDTO formation : formationService.getAllFormations()) {
            Map<String, LineupSlotDTO> slots = buildFormationMatrixSlots(userStarters, formation);
            V24MatchContext shapedContext = baseContext
                .withNewFormation(controlledTeamId, formation.name())
                .withSlots(controlledTeamId, slots);
            V24DetailedMatchEngine.TacticalShapeDebug shapeDebug = engine.debugTacticalShape(
                controlledIsHome ? home : away,
                userStarters,
                userBench,
                controlledIsHome ? homeStyle : awayStyle,
                formation.name(),
                slots);
            V24DetailedMatchResult result =
                engine.simulate(shapedContext, new Random(seed));
            ZoneCounts zones = countZones(result);
            rows.add(new FormationMatrixRow(
                formation.name(),
                result.homeGoals(),
                result.awayGoals(),
                result.homeXg(),
                result.awayXg(),
                result.homeShots(),
                result.awayShots(),
                result.homePossession(),
                result.awayPossession(),
                zones.homeCentral(),
                zones.homeWide(),
                zones.homeLong(),
                zones.awayCentral(),
                zones.awayWide(),
                zones.awayLong(),
                zones.homeLeftWide(),
                zones.homeRightWide(),
                zones.homeLeftWideXg(),
                zones.homeRightWideXg(),
                zones.awayLeftWide(),
                zones.awayRightWide(),
                zones.awayLeftWideXg(),
                zones.awayRightWideXg(),
                round3(shapeDebug.possessionMultiplier()),
                round3(shapeDebug.attackVolumeMultiplier()),
                round3(shapeDebug.defensiveResistanceMultiplier()),
                round3(shapeDebug.attackLeft()),
                round3(shapeDebug.attackCenter()),
                round3(shapeDebug.attackRight()),
                round3(shapeDebug.defenseLeft()),
                round3(shapeDebug.defenseCenter()),
                round3(shapeDebug.defenseRight())));
        }
        return rows;
    }

    private Map<String, LineupSlotDTO> buildFormationMatrixSlots(
            List<SessionPlayer> starters,
            FormationDTO formation) {
        List<FormationPositionDTO> positions = formation.positions().stream()
            .sorted(Comparator.comparing(FormationPositionDTO::index))
            .toList();
        if (positions.size() != starters.size()) {
            throw new IllegalStateException(
                "Formation " + formation.name() + " has " + positions.size()
                    + " positions for " + starters.size() + " starters");
        }

        List<SessionPlayer> remaining = new ArrayList<>(starters);
        Map<String, LineupSlotDTO> slots = new LinkedHashMap<>();
        for (FormationPositionDTO position : positions) {
            SessionPlayer player = pickBestPlayerForFormationPosition(remaining, position);
            if (player == null) {
                throw new IllegalStateException(
                    "Could not assign player to formation " + formation.name()
                        + " position " + position.role());
            }
            remaining.remove(player);
            slots.put(player.getSessionPlayerId(), new LineupSlotDTO(
                player.getSessionPlayerId(),
                position.subdivisionId(),
                position.xPercent(),
                position.yPercent()));
        }
        return slots;
    }

    private SessionPlayer pickBestPlayerForFormationPosition(
            List<SessionPlayer> candidates,
            FormationPositionDTO position) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
            .max(Comparator
                .comparingInt((SessionPlayer player) -> formationPositionFitScore(player, position))
                .thenComparingInt(this::formationMatrixPlayerStrength))
            .orElse(null);
    }

    private int formationPositionFitScore(SessionPlayer player, FormationPositionDTO position) {
        String playerProfile = matrixPlayerProfile(player);
        String slotProfile = matrixSlotProfile(position != null ? position.role() : null);
        int baseScore;
        if (playerProfile.equals(slotProfile)) baseScore = 100;
        else if ("WIDE_DEF".equals(slotProfile) && "DEF".equals(playerProfile)) baseScore = 92;
        else if ("DEF".equals(slotProfile) && "WIDE_DEF".equals(playerProfile)) baseScore = 90;
        else if ("WIDE_ATT".equals(slotProfile) && "ATT".equals(playerProfile)) baseScore = 88;
        else if ("ATT".equals(slotProfile) && "WIDE_ATT".equals(playerProfile)) baseScore = 86;
        else if ("AM".equals(slotProfile) && ("MID".equals(playerProfile) || "WIDE_ATT".equals(playerProfile))) baseScore = 82;
        else if ("MID".equals(slotProfile) && ("DM".equals(playerProfile) || "AM".equals(playerProfile))) baseScore = 80;
        else if ("DM".equals(slotProfile) && ("MID".equals(playerProfile) || "DEF".equals(playerProfile))) baseScore = 78;
        else if ("WIDE_MID".equals(slotProfile) && ("MID".equals(playerProfile) || "WIDE_ATT".equals(playerProfile) || "WIDE_DEF".equals(playerProfile))) baseScore = 76;
        else if ("MID".equals(slotProfile) && "WIDE_MID".equals(playerProfile)) baseScore = 74;
        else if ("ATT".equals(slotProfile) && "AM".equals(playerProfile)) baseScore = 70;
        else if ("AM".equals(slotProfile) && "ATT".equals(playerProfile)) baseScore = 68;
        else if ("DEF".equals(slotProfile) && "DM".equals(playerProfile)) baseScore = 66;
        else if ("DM".equals(slotProfile) && "WIDE_DEF".equals(playerProfile)) baseScore = 62;
        else if ("MID".equals(slotProfile) && ("DEF".equals(playerProfile) || "ATT".equals(playerProfile))) baseScore = 52;
        else if ("DEF".equals(slotProfile) && "MID".equals(playerProfile)) baseScore = 48;
        else if ("ATT".equals(slotProfile) && "MID".equals(playerProfile)) baseScore = 48;
        else if ("GK".equals(slotProfile) || "GK".equals(playerProfile)) baseScore = 0;
        else baseScore = 35;
        return baseScore + curatedMatrixSlotBonus(player, position);
    }

    private int curatedMatrixSlotBonus(SessionPlayer player, FormationPositionDTO slot) {
        CuratedMatrixRoleProfile profile = curatedMatrixRoleProfile(player);
        if (profile == null || slot == null || slot.role() == null) {
            return 0;
        }
        String role = slot.role().toUpperCase(Locale.ROOT);
        int bonus = 0;
        if (profile.roles().contains(role)) {
            bonus += 26;
        } else if (curatedMatrixRoleFamilyMatch(profile.roles(), role)) {
            bonus += 12;
        }
        String slotSide = matrixSlotSide(slot);
        if ("LEFT".equals(slotSide) || "RIGHT".equals(slotSide)) {
            if (profile.sides().contains(slotSide) || profile.sides().contains("BOTH")) {
                bonus += 22;
            } else if (profile.sides().contains(matrixOppositeSide(slotSide))) {
                bonus -= 34;
            }
        } else if ("CENTER".equals(slotSide) && profile.sides().contains("CENTER")) {
            bonus += 8;
        }
        return bonus;
    }

    private boolean curatedMatrixRoleFamilyMatch(Set<String> playerRoles, String slotRole) {
        if (Set.of("LB", "LWB", "LM", "LW").contains(slotRole)) {
            return playerRoles.stream().anyMatch(Set.of("LB", "LWB", "LM", "LW")::contains);
        }
        if (Set.of("RB", "RWB", "RM", "RW").contains(slotRole)) {
            return playerRoles.stream().anyMatch(Set.of("RB", "RWB", "RM", "RW")::contains);
        }
        if (Set.of("CB", "CDM", "CM", "CAM", "ST", "CF").contains(slotRole)) {
            return playerRoles.stream().anyMatch(Set.of("CB", "CDM", "CM", "CAM", "ST", "CF")::contains);
        }
        return false;
    }

    private String matrixSlotSide(FormationPositionDTO slot) {
        String role = slot.role() != null ? slot.role().toUpperCase(Locale.ROOT) : "";
        if (Set.of("LB", "LWB", "LM", "LW").contains(role)) return "LEFT";
        if (Set.of("RB", "RWB", "RM", "RW").contains(role)) return "RIGHT";
        if (Set.of("GK", "CB", "CDM", "CM", "ST", "CF").contains(role)) return "CENTER";
        Double x = slot.xPercent();
        if (x != null && x <= 42) return "LEFT";
        if (x != null && x >= 58) return "RIGHT";
        return "CENTER";
    }

    private String matrixOppositeSide(String side) {
        return "LEFT".equals(side) ? "RIGHT" : "LEFT";
    }

    private CuratedMatrixRoleProfile curatedMatrixRoleProfile(SessionPlayer player) {
        if (player == null || player.getName() == null) {
            return null;
        }
        return switch (normalizeMatrixPlayerName(player.getName())) {
            case "dani carvajal" -> new CuratedMatrixRoleProfile(Set.of("RB", "RWB"), Set.of("RIGHT"));
            case "david alaba" -> new CuratedMatrixRoleProfile(Set.of("CB", "LB"), Set.of("LEFT", "CENTER"));
            case "ferland mendy", "fran garcia" -> new CuratedMatrixRoleProfile(Set.of("LB", "LWB"), Set.of("LEFT"));
            case "lucas vazquez" -> new CuratedMatrixRoleProfile(Set.of("RB", "RM", "RWB"), Set.of("RIGHT"));
            case "vinicius junior" -> new CuratedMatrixRoleProfile(Set.of("LW", "LM"), Set.of("LEFT"));
            case "rodrygo goes" -> new CuratedMatrixRoleProfile(Set.of("RW", "LW", "ST", "CF"), Set.of("RIGHT", "BOTH"));
            case "brahim diaz" -> new CuratedMatrixRoleProfile(Set.of("RW", "CAM", "RM"), Set.of("RIGHT", "CENTER"));
            case "federico valverde" -> new CuratedMatrixRoleProfile(Set.of("CM", "RM", "CDM"), Set.of("CENTER", "RIGHT"));
            case "eduardo camavinga" -> new CuratedMatrixRoleProfile(Set.of("CM", "CDM", "LB"), Set.of("CENTER", "LEFT"));
            default -> null;
        };
    }

    private String normalizeMatrixPlayerName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

    private record CuratedMatrixRoleProfile(Set<String> roles, Set<String> sides) {}

    private String matrixPlayerProfile(SessionPlayer player) {
        if (player == null || player.getPosition() == null) return "MID";
        String position = player.getPosition().toUpperCase(Locale.ROOT);
        return switch (position) {
            case "GK" -> "GK";
            case "DEF" -> "DEF";
            case "MID" -> "MID";
            case "WINGER" -> "WIDE_ATT";
            case "ATT" -> "ATT";
            default -> "MID";
        };
    }

    private String matrixSlotProfile(String role) {
        if (role == null || role.isBlank()) return "MID";
        String normalized = role.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GK" -> "GK";
            case "LB", "RB", "LWB", "RWB" -> "WIDE_DEF";
            case "CB" -> "DEF";
            case "CDM", "DM" -> "DM";
            case "LM", "RM" -> "WIDE_MID";
            case "CAM", "AM" -> "AM";
            case "LW", "RW" -> "WIDE_ATT";
            case "ST", "CF" -> "ATT";
            default -> "MID";
        };
    }

    private int formationMatrixPlayerStrength(SessionPlayer player) {
        if (player == null) return 0;
        String profile = matrixPlayerProfile(player);
        return switch (profile) {
            case "GK" -> player.getDefense() + player.getMentality();
            case "DEF", "WIDE_DEF" -> player.getDefense() * 2 + player.getMentality() + player.getStamina();
            case "MID", "DM", "AM", "WIDE_MID" -> player.getTechnique() * 2 + player.getMentality() + player.getStamina();
            case "ATT", "WIDE_ATT" -> player.getAttack() * 2 + player.getTechnique() + player.getSpeed();
            default -> player.getAttack() + player.getDefense() + player.getTechnique() + player.getSpeed();
        };
    }

    @Override
    public Mono<List<FormationMatrixSummaryRow>> runFormationMatrixSummary(
            UUID userId,
            String matchId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        if (seedCount < 1 || seedCount > 100) {
            return Mono.error(new IllegalArgumentException("seedCount must be between 1 and 100"));
        }

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                return Mono.fromSupplier(() ->
                    executeFormationMatrixSummary(optionalCareer.get(), matchId, seedStart, seedCount, controlledTeamSide));
            });
    }

    private List<FormationMatrixSummaryRow> executeFormationMatrixSummary(
            CareerSave career,
            String matchId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));
        String controlledTeamId = resolveControlledTeamId(career, fixture, controlledTeamSide);
        boolean controlledIsHome = fixture.getHomeTeamId().equals(controlledTeamId);

        Map<String, FormationSummaryAccumulator> byFormation = new LinkedHashMap<>();
        for (int i = 0; i < seedCount; i++) {
            long seed = seedStart + i;
            for (FormationMatrixRow row : executeFormationMatrix(career, matchId, seed, controlledTeamSide)) {
                byFormation
                    .computeIfAbsent(row.formation(), FormationSummaryAccumulator::new)
                    .add(row, controlledIsHome);
            }
        }

        return byFormation.values().stream()
            .map(acc -> acc.toRow(seedStart, seedCount))
            .toList();
    }

    @Override
    public Mono<List<SideMirrorSyntheticLabRow>> runSideMirrorSyntheticLab(
            UUID userId,
            long seedStart,
            int seedCount) {
        if (seedCount < 1 || seedCount > 100) {
            return Mono.error(new IllegalArgumentException("seedCount must be between 1 and 100"));
        }
        return Mono.fromSupplier(() -> executeSideMirrorSyntheticLab(seedStart, seedCount));
    }

    private List<SideMirrorSyntheticLabRow> executeSideMirrorSyntheticLab(long seedStart, int seedCount) {
        List<SideMirrorSyntheticLabRow> rows = new ArrayList<>();
        for (FormationDTO formation : formationService.getAllFormations()) {
            FormationSummaryAccumulator weakLeft = new FormationSummaryAccumulator(formation.name());
            FormationSummaryAccumulator weakRight = new FormationSummaryAccumulator(formation.name());
            for (int i = 0; i < seedCount; i++) {
                long seed = seedStart + i;
                weakLeft.add(executeSyntheticSideMirrorRow(formation, seed, true), true);
                weakRight.add(executeSyntheticSideMirrorRow(formation, seed, false), true);
            }
            FormationMatrixSummaryRow left = weakLeft.toRow(seedStart, seedCount);
            FormationMatrixSummaryRow right = weakRight.toRow(seedStart, seedCount);
            rows.add(toSyntheticSideMirrorRow(formation.name(), seedStart, seedCount, left, right));
        }
        return rows;
    }

    private FormationMatrixRow executeSyntheticSideMirrorRow(
            FormationDTO formation,
            long seed,
            boolean weakenOpponentLeft) {
        SessionTeam home = syntheticTeam("synthetic-home", "Synthetic Probe", formation.name());
        SessionTeam away = syntheticTeam("synthetic-away", "Synthetic Mirror", formation.name());
        List<SessionPlayer> homeStarters = syntheticPlayers("H", false);
        List<SessionPlayer> awayStarters = syntheticPlayers("A", false);
        Map<String, LineupSlotDTO> homeSlots = buildFormationMatrixSlots(homeStarters, formation);
        Map<String, LineupSlotDTO> awaySlots = buildFormationMatrixSlots(awayStarters, formation);
        weakenSyntheticWideDefender(awayStarters, awaySlots, weakenOpponentLeft);

        V24MatchContext context = new V24MatchContext(
            "synthetic-side-mirror-" + formation.name() + "-" + (weakenOpponentLeft ? "WL" : "WR") + "-" + seed,
            home.getSessionTeamId(),
            away.getSessionTeamId(),
            home,
            away,
            homeStarters,
            awayStarters,
            List.of(),
            List.of(),
            formation.name(),
            formation.name(),
            TeamStyle.BALANCED,
            TeamStyle.BALANCED,
            List.of(),
            homeSlots,
            awaySlots);

        V24DetailedMatchEngine engine = new V24DetailedMatchEngine();
        V24DetailedMatchEngine.TacticalShapeDebug shapeDebug = engine.debugTacticalShape(
            home,
            homeStarters,
            List.of(),
            TeamStyle.BALANCED,
            formation.name(),
            homeSlots);
        V24DetailedMatchResult result = engine.simulate(context, new Random(seed));
        ZoneCounts zones = countZones(result);
        return new FormationMatrixRow(
            formation.name(),
            result.homeGoals(),
            result.awayGoals(),
            result.homeXg(),
            result.awayXg(),
            result.homeShots(),
            result.awayShots(),
            result.homePossession(),
            result.awayPossession(),
            zones.homeCentral(),
            zones.homeWide(),
            zones.homeLong(),
            zones.awayCentral(),
            zones.awayWide(),
            zones.awayLong(),
            zones.homeLeftWide(),
            zones.homeRightWide(),
            zones.homeLeftWideXg(),
            zones.homeRightWideXg(),
            zones.awayLeftWide(),
            zones.awayRightWide(),
            zones.awayLeftWideXg(),
            zones.awayRightWideXg(),
            round3(shapeDebug.possessionMultiplier()),
            round3(shapeDebug.attackVolumeMultiplier()),
            round3(shapeDebug.defensiveResistanceMultiplier()),
            round3(shapeDebug.attackLeft()),
            round3(shapeDebug.attackCenter()),
            round3(shapeDebug.attackRight()),
            round3(shapeDebug.defenseLeft()),
            round3(shapeDebug.defenseCenter()),
            round3(shapeDebug.defenseRight()));
    }

    private SideMirrorSyntheticLabRow toSyntheticSideMirrorRow(
            String formation,
            long seedStart,
            int seedCount,
            FormationMatrixSummaryRow weakLeft,
            FormationMatrixSummaryRow weakRight) {
        double weakLeftRightEdge = round3(weakLeft.avgRightWideXgFor() - weakLeft.avgLeftWideXgFor());
        double weakRightLeftEdge = round3(weakRight.avgLeftWideXgFor() - weakRight.avgRightWideXgFor());
        double mirrorGap = round3(weakLeftRightEdge - weakRightLeftEdge);
        boolean weakLeftOk = weakLeftRightEdge >= 0.015;
        boolean weakRightOk = weakRightLeftEdge >= 0.015;
        boolean conservativeLowBlock = "5-4-1".equals(formation)
            && Math.abs(mirrorGap) <= 0.025
            && !weakLeftOk
            && !weakRightOk;
        String verdict = weakLeftOk && weakRightOk
            ? "OK"
            : (weakLeftOk || weakRightOk || conservativeLowBlock ? "Parcial" : "Revisar");
        String read = "OK".equals(verdict)
            ? "Laboratorio sintetico espejo responde en ambos sentidos."
            : "Parcial".equals(verdict)
                ? (conservativeLowBlock
                    ? "5-4-1 bloque bajo: baja senal lateral ofensiva esperable; validar con low block lab antes de tocar motor."
                    : "3-5-2".equals(formation)
                        ? "3-5-2: un carril responde y el otro queda plano; validar carrileros/seeds antes de tocar motor."
                        : "4-2-2-2".equals(formation)
                            ? "4-2-2-2: sin carrileros naturales; la amplitud depende de mediapuntas/delanteros y puede responder asimetrica."
                    : "Un lado responde mas que el otro aun sin sesgo de plantel; revisar calibracion lateral.")
                : "Sin senal lateral suficiente en laboratorio sintetico; revisar motor.";
        return new SideMirrorSyntheticLabRow(
            formation,
            seedStart,
            seedStart + seedCount - 1L,
            seedCount,
            weakLeft.avgLeftWideXgFor(),
            weakLeft.avgRightWideXgFor(),
            weakRight.avgLeftWideXgFor(),
            weakRight.avgRightWideXgFor(),
            weakLeft.avgLeftWideShotsFor(),
            weakLeft.avgRightWideShotsFor(),
            weakRight.avgLeftWideShotsFor(),
            weakRight.avgRightWideShotsFor(),
            weakLeftRightEdge,
            weakRightLeftEdge,
            mirrorGap,
            verdict,
            read);
    }

    private SessionTeam syntheticTeam(String id, String name, String formation) {
        SessionTeam team = SessionTeam.custom(id, name, "LAB", BigDecimal.ZERO, formation);
        team.setSessionTeamId(id);
        team.setStyle(TeamStyle.BALANCED);
        return team;
    }

    private List<SessionPlayer> syntheticPlayers(String prefix, boolean weak) {
        List<SessionPlayer> players = new ArrayList<>();
        players.add(syntheticPlayer(prefix + "-GK", "GK", 74, weak));
        players.add(syntheticPlayer(prefix + "-DEF-L", "DEF", 76, weak));
        players.add(syntheticPlayer(prefix + "-DEF-CL", "DEF", 76, weak));
        players.add(syntheticPlayer(prefix + "-DEF-CR", "DEF", 76, weak));
        players.add(syntheticPlayer(prefix + "-DEF-R", "DEF", 76, weak));
        players.add(syntheticPlayer(prefix + "-MID-L", "MID", 76, weak));
        players.add(syntheticPlayer(prefix + "-MID-C", "MID", 76, weak));
        players.add(syntheticPlayer(prefix + "-MID-R", "MID", 76, weak));
        players.add(syntheticPlayer(prefix + "-WING-L", "WINGER", 76, weak));
        players.add(syntheticPlayer(prefix + "-WING-R", "WINGER", 76, weak));
        players.add(syntheticPlayer(prefix + "-ATT", "ATT", 76, weak));
        return players;
    }

    private SessionPlayer syntheticPlayer(String id, String position, int overall, boolean weak) {
        SessionPlayer player = SessionPlayer.custom(
            "Lab " + id,
            25,
            position,
            overall,
            overall,
            overall,
            overall,
            overall,
            overall,
            BigDecimal.ZERO);
        player.setSessionPlayerId(id);
        if (weak) {
            player.setDefense(42);
            player.setMentality(45);
            player.setStamina(55);
        }
        return player;
    }

    private void weakenSyntheticWideDefender(
            List<SessionPlayer> starters,
            Map<String, LineupSlotDTO> slots,
            boolean leftSide) {
        if (starters == null || starters.isEmpty() || slots == null || slots.isEmpty()) return;
        Optional<Map.Entry<String, LineupSlotDTO>> target = slots.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .filter(entry -> {
                Double y = entry.getValue().customYPercent();
                return y != null && y >= 55.0;
            })
            .min((a, b) -> {
                double ax = Optional.ofNullable(a.getValue().customXPercent()).orElse(50.0);
                double bx = Optional.ofNullable(b.getValue().customXPercent()).orElse(50.0);
                return leftSide ? Double.compare(ax, bx) : Double.compare(bx, ax);
            });
        if (target.isEmpty()) return;
        String playerId = target.get().getKey();
        starters.stream()
            .filter(player -> playerId.equals(player.getSessionPlayerId()))
            .findFirst()
            .ifPresent(player -> {
                player.setDefense(42);
                player.setMentality(45);
                player.setStamina(55);
            });
    }

    @Override
    public Mono<List<ScenarioMatrixRow>> runScenarioMatrix(UUID userId, String matchId, Long seedOverride) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        long seed = (seedOverride != null) ? seedOverride : 12345L;

        return loadLiveCareer(userId)
            .flatMap(career -> Mono.fromSupplier(() -> executeScenarioMatrix(career, matchId, seed)));
    }

    @Override
    public Mono<List<ScenarioMatrixSummaryRow>> runScenarioMatrixSummary(
            UUID userId,
            String matchId,
            long seedStart,
            int seedCount,
            String scenarioGroup,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        if (seedCount < 1 || seedCount > 100) {
            return Mono.error(new IllegalArgumentException("seedCount must be between 1 and 100"));
        }

        return loadLiveCareer(userId)
            .flatMap(career -> Mono.fromSupplier(() ->
                executeScenarioMatrixSummary(career, matchId, seedStart, seedCount, scenarioGroup, controlledTeamSide)));
    }

    /**
     * Panel C in the debug harness is fed by CareerSessionService
     * (/career/fixtures/round-with-bye). Scenario matrix actions must read the
     * same live career source, otherwise the UI can select a matchId from the
     * cache while the smoke looks in an older Redis snapshot and reports a
     * misleading 404 "Match not found".
     *
     * <p>Keep a repository fallback for older unit tests and defensive local
     * flows where the cache facade is unavailable or returns empty.
     */
    private Mono<CareerSave> loadLiveCareer(UUID userId) {
        Mono<CareerSave> cachedCareer = null;
        if (careerSessionService != null) {
            cachedCareer = careerSessionService.getCareerFromCache(userId);
        }
        Mono<CareerSave> repositoryCareer = Mono.defer(() -> careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> optionalCareer
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new IllegalStateException(
                    "Career not found for userId=" + userId)))));

        return cachedCareer != null
            ? cachedCareer.switchIfEmpty(repositoryCareer)
            : repositoryCareer;
    }

    @Override
    public Mono<PlayerSwapMatrixSummaryRow> runPlayerSwapMatrixSummary(
            UUID userId,
            String matchId,
            String starterPlayerId,
            String benchPlayerId,
            String slotId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        if (starterPlayerId == null || starterPlayerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("starterPlayerId is required"));
        }
        if (benchPlayerId == null || benchPlayerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("benchPlayerId is required"));
        }
        if (starterPlayerId.equals(benchPlayerId)) {
            return Mono.error(new IllegalArgumentException("starterPlayerId and benchPlayerId must differ"));
        }
        if (seedCount < 1 || seedCount > 100) {
            return Mono.error(new IllegalArgumentException("seedCount must be between 1 and 100"));
        }

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                return Mono.fromSupplier(() -> executePlayerSwapMatrixSummary(
                    optionalCareer.get(),
                    matchId,
                    starterPlayerId,
                    benchPlayerId,
                    slotId,
                    seedStart,
                    seedCount,
                    controlledTeamSide));
            });
    }

    public Mono<PlayerSwapMatrixSummaryRow> runPlayerSwapMatrixSummary(
            UUID userId,
            String matchId,
            String starterPlayerId,
            String benchPlayerId,
            String slotId,
            long seedStart,
            int seedCount) {
        return runPlayerSwapMatrixSummary(
            userId,
            matchId,
            starterPlayerId,
            benchPlayerId,
            slotId,
            seedStart,
            seedCount,
            null);
    }

    @Override
    public Mono<SubstitutionWhatIfSummaryRow> runSubstitutionWhatIfSummary(
            UUID userId,
            String matchId,
            String playerOffId,
            String playerOnId,
            Integer minute,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        if (playerOffId == null || playerOffId.isBlank()) {
            return Mono.error(new IllegalArgumentException("playerOffId is required"));
        }
        if (playerOnId == null || playerOnId.isBlank()) {
            return Mono.error(new IllegalArgumentException("playerOnId is required"));
        }
        if (playerOffId.equals(playerOnId)) {
            return Mono.error(new IllegalArgumentException("playerOffId and playerOnId must differ"));
        }
        int effectiveMinute = minute != null ? minute : 60;
        if (effectiveMinute < 0 || effectiveMinute > 90) {
            return Mono.error(new IllegalArgumentException("minute must be between 0 and 90"));
        }
        if (seedCount < 1 || seedCount > 100) {
            return Mono.error(new IllegalArgumentException("seedCount must be between 1 and 100"));
        }

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                return Mono.fromSupplier(() -> executeSubstitutionWhatIfSummary(
                    optionalCareer.get(),
                    matchId,
                    playerOffId,
                    playerOnId,
                    effectiveMinute,
                    seedStart,
                    seedCount,
                    controlledTeamSide));
            });
    }

    private SubstitutionWhatIfSummaryRow executeSubstitutionWhatIfSummary(
            CareerSave career,
            String matchId,
            String playerOffId,
            String playerOnId,
            int minute,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {

        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));

        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalStateException("SessionTeam not found for match " + matchId);
        }

        String controlledTeamId = resolveControlledTeamId(career, fixture, controlledTeamSide);
        boolean userIsHome = fixture.getHomeTeamId().equals(controlledTeamId);
        boolean userIsAway = fixture.getAwayTeamId().equals(controlledTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException(
                "Substitution what-if controlled team is not part of match: " + controlledTeamId);
        }

        TeamStyle homeStyle = home.getStyle() != null ? home.getStyle() : TeamStyle.BALANCED;
        TeamStyle awayStyle = away.getStyle() != null ? away.getStyle() : TeamStyle.BALANCED;
        V24MatchContext baseContext = v24ContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            homeStyle,
            awayStyle,
            seedStart);
        List<SessionPlayer> starters = userIsHome
            ? baseContext.homeStartingPlayers()
            : baseContext.awayStartingPlayers();
        List<SessionPlayer> bench = userIsHome
            ? baseContext.homeBenchPlayers()
            : baseContext.awayBenchPlayers();
        SessionPlayer off = findPlayer(starters, playerOffId)
            .orElseThrow(() -> new IllegalArgumentException(
                "playerOffId '" + playerOffId + "' not in controlled starting XI"));
        SessionPlayer on = findPlayer(bench, playerOnId)
            .orElseThrow(() -> new IllegalArgumentException(
                "playerOnId '" + playerOnId + "' not on controlled bench"));

        SwapAccumulator baseline = new SwapAccumulator();
        SwapAccumulator substituted = new SwapAccumulator();
        for (int i = 0; i < seedCount; i++) {
            long seed = seedStart + i;
            V24MatchContext seededBase = v24ContextFactory.buildWithStyles(
                career,
                fixture,
                home,
                away,
                homeStyle,
                awayStyle,
                seed);
            V24DetailedMatchResult baselineResult =
                simulateWithNoopReplay(seededBase, minute, seed);
            V24DetailedMatchResult substitutedResult =
                simulateWithManualSubstitution(seededBase, controlledTeamId, playerOffId, playerOnId, minute, seed);
            baseline.add(baselineResult, userIsHome);
            substituted.add(substitutedResult, userIsHome);
        }

        SwapAverages baseAvg = baseline.averages();
        SwapAverages subAvg = substituted.averages();
        String formation = currentFormation(career, controlledTeamId, userIsHome ? home : away);
        double deltaXgFor = round3(subAvg.xgFor() - baseAvg.xgFor());
        double deltaXgAgainst = round3(subAvg.xgAgainst() - baseAvg.xgAgainst());
        double deltaShotsFor = round2(subAvg.shotsFor() - baseAvg.shotsFor());
        String read = safeName(off) + " -> " + safeName(on)
            + " min " + minute
            + " | ΔxG " + deltaXgFor
            + " | Δshots " + deltaShotsFor
            + " | ΔxGA " + deltaXgAgainst;

        return new SubstitutionWhatIfSummaryRow(
            matchId,
            formation,
            minute,
            seedStart,
            seedStart + seedCount - 1L,
            seedCount,
            playerOffId,
            safeName(off),
            off.getPosition(),
            playerOverall(off),
            playerOnId,
            safeName(on),
            on.getPosition(),
            playerOverall(on),
            baseAvg.goalsFor(),
            baseAvg.goalsAgainst(),
            baseAvg.goalDiff(),
            baseAvg.shotsFor(),
            baseAvg.shotsAgainst(),
            baseAvg.possessionFor(),
            baseAvg.xgFor(),
            baseAvg.xgAgainst(),
            baseAvg.xgDiff(),
            subAvg.goalsFor(),
            subAvg.goalsAgainst(),
            subAvg.goalDiff(),
            subAvg.shotsFor(),
            subAvg.shotsAgainst(),
            subAvg.possessionFor(),
            subAvg.xgFor(),
            subAvg.xgAgainst(),
            subAvg.xgDiff(),
            round2(subAvg.goalsFor() - baseAvg.goalsFor()),
            round2(subAvg.goalsAgainst() - baseAvg.goalsAgainst()),
            round2(subAvg.goalDiff() - baseAvg.goalDiff()),
            deltaShotsFor,
            round2(subAvg.shotsAgainst() - baseAvg.shotsAgainst()),
            round2(subAvg.possessionFor() - baseAvg.possessionFor()),
            deltaXgFor,
            deltaXgAgainst,
            round3(subAvg.xgDiff() - baseAvg.xgDiff()),
            round2(subAvg.centralShotsFor() - baseAvg.centralShotsFor()),
            round2(subAvg.wideShotsFor() - baseAvg.wideShotsFor()),
            round2(subAvg.longShotsFor() - baseAvg.longShotsFor()),
            round3(subAvg.centralXgFor() - baseAvg.centralXgFor()),
            round3(subAvg.wideXgFor() - baseAvg.wideXgFor()),
            round3(subAvg.longXgFor() - baseAvg.longXgFor()),
            read);
    }

    private V24DetailedMatchResult simulateWithManualSubstitution(
            V24MatchContext context,
            String teamId,
            String playerOffId,
            String playerOnId,
            int minute,
            long seed) {
        V24LiveSession session = new V24LiveSession(context, seed);
        for (int i = 0; i < minute; i++) {
            session.tick();
        }
        session.mutateContext(ctx -> ctx.withManualSubstitution(teamId, playerOffId, playerOnId, minute));
        while (!session.isFinished()) {
            session.tick();
        }
        return session.finalResult();
    }

    private V24DetailedMatchResult simulateWithNoopReplay(
            V24MatchContext context,
            int minute,
            long seed) {
        V24LiveSession session = new V24LiveSession(context, seed);
        for (int i = 0; i < minute; i++) {
            session.tick();
        }
        session.mutateContext(ctx -> ctx);
        while (!session.isFinished()) {
            session.tick();
        }
        return session.finalResult();
    }

    private PlayerSwapMatrixSummaryRow executePlayerSwapMatrixSummary(
            CareerSave career,
            String matchId,
            String starterPlayerId,
            String benchPlayerId,
            String requestedSlotId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {

        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));

        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalStateException("SessionTeam not found for match " + matchId);
        }

        String controlledTeamId = resolveControlledTeamId(career, fixture, controlledTeamSide);
        boolean userIsHome = fixture.getHomeTeamId().equals(controlledTeamId);
        boolean userIsAway = fixture.getAwayTeamId().equals(controlledTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException(
                "Player swap matrix controlled team is not part of match: " + controlledTeamId);
        }
        TeamStyle homeStyle = home.getStyle() != null ? home.getStyle() : TeamStyle.BALANCED;
        TeamStyle awayStyle = away.getStyle() != null ? away.getStyle() : TeamStyle.BALANCED;

        V24MatchContext baseContext = v24ContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            homeStyle,
            awayStyle,
            seedStart);
        List<SessionPlayer> userStarters = userIsHome
            ? baseContext.homeStartingPlayers()
            : baseContext.awayStartingPlayers();
        List<SessionPlayer> userBench = userIsHome
            ? baseContext.homeBenchPlayers()
            : baseContext.awayBenchPlayers();

        String resolvedStarterPlayerId = starterPlayerId;
        String resolvedBenchPlayerId = benchPlayerId;
        if (isAutoPlayerSwapToken(resolvedStarterPlayerId) || isAutoPlayerSwapToken(resolvedBenchPlayerId)) {
            String autoMode = autoPlayerSwapMode(resolvedStarterPlayerId, resolvedBenchPlayerId);
            PlayerSwapAutoPair pair = chooseAutoPlayerSwapPair(userStarters, userBench, autoMode)
                .orElseThrow(() -> new IllegalArgumentException(
                    "No automatic starter/bench swap candidate found in V24 match context for mode " + autoMode));
            resolvedStarterPlayerId = pair.starter().getSessionPlayerId();
            resolvedBenchPlayerId = pair.bench().getSessionPlayerId();
        }

        final String effectiveStarterPlayerId = resolvedStarterPlayerId;
        final String effectiveBenchPlayerId = resolvedBenchPlayerId;
        SessionPlayer starter = findPlayer(userStarters, effectiveStarterPlayerId)
            .orElseThrow(() -> new IllegalArgumentException(
                "starterPlayerId '" + effectiveStarterPlayerId + "' not in user starting XI"));
        SessionPlayer bench = findPlayer(userBench, effectiveBenchPlayerId)
            .orElseThrow(() -> new IllegalArgumentException(
                "benchPlayerId '" + effectiveBenchPlayerId + "' not on user bench"));

        String formation = currentFormation(career, controlledTeamId, userIsHome ? home : away);
        String slotId = resolveSlotId(baseContext, userIsHome, effectiveStarterPlayerId, requestedSlotId);
        SwapAccumulator baseline = new SwapAccumulator();
        SwapAccumulator swapped = new SwapAccumulator();
        SwapAccumulator baselinePreAutoSub = new SwapAccumulator();
        SwapAccumulator swappedPreAutoSub = new SwapAccumulator();

        for (int i = 0; i < seedCount; i++) {
            long seed = seedStart + i;
            V24MatchContext seededBase = v24ContextFactory.buildWithStyles(
                career,
                fixture,
                home,
                away,
                homeStyle,
                awayStyle,
                seed);
            V24DetailedMatchResult baselineResult =
                new V24DetailedMatchEngine().simulate(seededBase, new Random(seed));
            V24MatchContext swappedContext = buildInitialSwapContext(
                seededBase, controlledTeamId, effectiveStarterPlayerId, effectiveBenchPlayerId);
            V24DetailedMatchResult swappedResult =
                new V24DetailedMatchEngine().simulate(swappedContext, new Random(seed));
            V24DetailedMatchResult baselinePreAutoSubResult =
                new V24DetailedMatchEngine().simulate(seededBase, new Random(seed), 59);
            V24DetailedMatchResult swappedPreAutoSubResult =
                new V24DetailedMatchEngine().simulate(swappedContext, new Random(seed), 59);
            baseline.add(baselineResult, userIsHome);
            swapped.add(swappedResult, userIsHome);
            baselinePreAutoSub.add(baselinePreAutoSubResult, userIsHome);
            swappedPreAutoSub.add(swappedPreAutoSubResult, userIsHome);
        }

        SwapAverages baseAvg = baseline.averages();
        SwapAverages swapAvg = swapped.averages();
        SwapAverages basePreAutoSubAvg = baselinePreAutoSub.averages();
        SwapAverages swapPreAutoSubAvg = swappedPreAutoSub.averages();
        return new PlayerSwapMatrixSummaryRow(
            matchId,
            formation,
            slotId,
            seedStart,
            seedStart + seedCount - 1L,
            seedCount,
            effectiveStarterPlayerId,
            safeName(starter),
            starter.getPosition(),
            playerOverall(starter),
            effectiveBenchPlayerId,
            safeName(bench),
            bench.getPosition(),
            playerOverall(bench),
            baseAvg.goalsFor(),
            baseAvg.goalsAgainst(),
            baseAvg.goalDiff(),
            baseAvg.shotsFor(),
            baseAvg.shotsAgainst(),
            baseAvg.possessionFor(),
            baseAvg.xgFor(),
            baseAvg.xgAgainst(),
            baseAvg.xgDiff(),
            baseAvg.centralShotsFor(),
            baseAvg.wideShotsFor(),
            baseAvg.longShotsFor(),
            baseAvg.centralShotsAgainst(),
            baseAvg.wideShotsAgainst(),
            baseAvg.longShotsAgainst(),
            baseAvg.centralXgFor(),
            baseAvg.wideXgFor(),
            baseAvg.longXgFor(),
            baseAvg.centralXgAgainst(),
            baseAvg.wideXgAgainst(),
            baseAvg.longXgAgainst(),
            swapAvg.goalsFor(),
            swapAvg.goalsAgainst(),
            swapAvg.goalDiff(),
            swapAvg.shotsFor(),
            swapAvg.shotsAgainst(),
            swapAvg.possessionFor(),
            swapAvg.xgFor(),
            swapAvg.xgAgainst(),
            swapAvg.xgDiff(),
            swapAvg.centralShotsFor(),
            swapAvg.wideShotsFor(),
            swapAvg.longShotsFor(),
            swapAvg.centralShotsAgainst(),
            swapAvg.wideShotsAgainst(),
            swapAvg.longShotsAgainst(),
            swapAvg.centralXgFor(),
            swapAvg.wideXgFor(),
            swapAvg.longXgFor(),
            swapAvg.centralXgAgainst(),
            swapAvg.wideXgAgainst(),
            swapAvg.longXgAgainst(),
            round2(swapAvg.goalsFor() - baseAvg.goalsFor()),
            round2(swapAvg.goalsAgainst() - baseAvg.goalsAgainst()),
            round2(swapAvg.goalDiff() - baseAvg.goalDiff()),
            round2(swapAvg.shotsFor() - baseAvg.shotsFor()),
            round2(swapAvg.shotsAgainst() - baseAvg.shotsAgainst()),
            round2(swapAvg.possessionFor() - baseAvg.possessionFor()),
            round3(swapAvg.xgFor() - baseAvg.xgFor()),
            round3(swapAvg.xgAgainst() - baseAvg.xgAgainst()),
            round3(swapAvg.xgDiff() - baseAvg.xgDiff()),
            round2(swapAvg.centralShotsFor() - baseAvg.centralShotsFor()),
            round2(swapAvg.wideShotsFor() - baseAvg.wideShotsFor()),
            round2(swapAvg.longShotsFor() - baseAvg.longShotsFor()),
            round2(swapAvg.centralShotsAgainst() - baseAvg.centralShotsAgainst()),
            round2(swapAvg.wideShotsAgainst() - baseAvg.wideShotsAgainst()),
            round2(swapAvg.longShotsAgainst() - baseAvg.longShotsAgainst()),
            round3(swapAvg.centralXgFor() - baseAvg.centralXgFor()),
            round3(swapAvg.wideXgFor() - baseAvg.wideXgFor()),
            round3(swapAvg.longXgFor() - baseAvg.longXgFor()),
            round3(swapAvg.centralXgAgainst() - baseAvg.centralXgAgainst()),
            round3(swapAvg.wideXgAgainst() - baseAvg.wideXgAgainst()),
            round3(swapAvg.longXgAgainst() - baseAvg.longXgAgainst()),
            round2(swapPreAutoSubAvg.shotsFor() - basePreAutoSubAvg.shotsFor()),
            round2(swapPreAutoSubAvg.shotsAgainst() - basePreAutoSubAvg.shotsAgainst()),
            round3(swapPreAutoSubAvg.xgFor() - basePreAutoSubAvg.xgFor()),
            round3(swapPreAutoSubAvg.xgAgainst() - basePreAutoSubAvg.xgAgainst()),
            round3(swapPreAutoSubAvg.xgDiff() - basePreAutoSubAvg.xgDiff()));
    }

    @Override
    public Mono<PositionPixelMatrixSummaryRow> runPositionPixelMatrixSummary(
            UUID userId,
            String matchId,
            String playerId,
            Double targetXPercent,
            Double targetYPercent,
            Double deltaXPercent,
            Double deltaYPercent,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        if (playerId == null || playerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("playerId is required"));
        }
        boolean hasAbsoluteTarget = targetXPercent != null && targetYPercent != null;
        boolean hasRelativeDelta = deltaXPercent != null && deltaYPercent != null;
        if (!hasAbsoluteTarget && !hasRelativeDelta) {
            return Mono.error(new IllegalArgumentException(
                "targetXPercent/targetYPercent or deltaXPercent/deltaYPercent are required"));
        }
        if (seedCount < 1 || seedCount > 100) {
            return Mono.error(new IllegalArgumentException("seedCount must be between 1 and 100"));
        }
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException("Career not found for userId=" + userId));
                }
                return Mono.fromSupplier(() -> executePositionPixelMatrixSummary(
                    optionalCareer.get(),
                    matchId,
                    playerId,
                    hasAbsoluteTarget ? clampPercent(targetXPercent) : null,
                    hasAbsoluteTarget ? clampPercent(targetYPercent) : null,
                    hasRelativeDelta ? deltaXPercent : null,
                    hasRelativeDelta ? deltaYPercent : null,
                    seedStart,
                    seedCount,
                    controlledTeamSide));
            });
    }

    public Mono<PositionPixelMatrixSummaryRow> runPositionPixelMatrixSummary(
            UUID userId,
            String matchId,
            String playerId,
            Double targetXPercent,
            Double targetYPercent,
            Double deltaXPercent,
            Double deltaYPercent,
            long seedStart,
            int seedCount) {
        return runPositionPixelMatrixSummary(
            userId,
            matchId,
            playerId,
            targetXPercent,
            targetYPercent,
            deltaXPercent,
            deltaYPercent,
            seedStart,
            seedCount,
            null);
    }

    @Override
    public Mono<List<RoleSlotImpactSummaryRow>> runRoleSlotImpactSummary(
            UUID userId,
            String matchId,
            String slotId,
            List<String> naturalPositions,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        if (slotId == null || slotId.isBlank()) {
            return Mono.error(new IllegalArgumentException("slotId is required"));
        }
        if (seedCount < 1 || seedCount > 100) {
            return Mono.error(new IllegalArgumentException("seedCount must be between 1 and 100"));
        }
        List<String> safeNaturalPositions = naturalPositions == null || naturalPositions.isEmpty()
            ? List.of("WINGER", "MID", "ATT", "DEF")
            : naturalPositions.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (safeNaturalPositions.isEmpty()) {
            return Mono.error(new IllegalArgumentException("naturalPositions must contain at least one position"));
        }

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException("Career not found for userId=" + userId));
                }
                return Mono.fromSupplier(() -> executeRoleSlotImpactSummary(
                    optionalCareer.get(),
                    matchId,
                    slotId,
                    safeNaturalPositions,
                    seedStart,
                    seedCount,
                    controlledTeamSide));
            });
    }

    private List<RoleSlotImpactSummaryRow> executeRoleSlotImpactSummary(
            CareerSave career,
            String matchId,
            String slotId,
            List<String> naturalPositions,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Match not found in current tournament: " + matchId));
        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalStateException("SessionTeam not found for match " + matchId);
        }
        String controlledTeamId = resolveControlledTeamId(career, fixture, controlledTeamSide);
        boolean userIsHome = fixture.getHomeTeamId().equals(controlledTeamId);
        boolean userIsAway = fixture.getAwayTeamId().equals(controlledTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException("Role slot impact controlled team is not part of match: " + controlledTeamId);
        }
        TeamStyle homeStyle = home.getStyle() != null ? home.getStyle() : TeamStyle.BALANCED;
        TeamStyle awayStyle = away.getStyle() != null ? away.getStyle() : TeamStyle.BALANCED;

        V24MatchContext baseContext = v24ContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            homeStyle,
            awayStyle,
            seedStart);
        Map<String, LineupSlotDTO> userSlots = userIsHome
            ? baseContext.homeSlotsByPlayerId()
            : baseContext.awaySlotsByPlayerId();
        LineupSlotDTO slot = userSlots.values().stream()
            .filter(s -> s != null && slotId.equals(s.subdivisionId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("slotId '" + slotId + "' not found in controlled team lineup"));
        List<SessionPlayer> starters = userIsHome ? baseContext.homeStartingPlayers() : baseContext.awayStartingPlayers();
        SessionPlayer baselinePlayer = findPlayer(starters, slot.playerId())
            .orElseThrow(() -> new IllegalArgumentException("slot player not found in starting XI: " + slot.playerId()));

        double x = slot.customXPercent() != null ? slot.customXPercent() : canonicalXPercent(slotId).orElse(50.0);
        double y = slot.customYPercent() != null ? slot.customYPercent() : canonicalYPercent(slotId).orElse(fallbackYPercent(baselinePlayer.getPosition()));
        String formation = currentFormation(career, controlledTeamId, userIsHome ? home : away);

        List<RoleSlotImpactSummaryRow> rows = new ArrayList<>();
        for (String natural : naturalPositions) {
            PositionPixelPlayerDiagnostic diagnostic =
                positionPixelPlayerDiagnostic(roleOverrideClone(baselinePlayer, natural), slotId, x, y);
            SwapAccumulator accumulator = new SwapAccumulator();
            for (int i = 0; i < seedCount; i++) {
                long seed = seedStart + i;
                V24MatchContext seededBase = v24ContextFactory.buildWithStyles(
                    career,
                    fixture,
                    home,
                    away,
                    homeStyle,
                    awayStyle,
                    seed);
                V24MatchContext roleContext = buildRoleOverrideContext(
                    seededBase,
                    controlledTeamId,
                    baselinePlayer.getSessionPlayerId(),
                    natural);
                V24DetailedMatchResult result =
                    new V24DetailedMatchEngine().simulate(roleContext, new Random(seed));
                accumulator.add(result, userIsHome);
            }
            SwapAverages avg = accumulator.averages();
            rows.add(new RoleSlotImpactSummaryRow(
                matchId,
                formation,
                slotId,
                round2(x),
                round2(y),
                baselinePlayer.getSessionPlayerId(),
                safeName(baselinePlayer),
                baselinePlayer.getPosition(),
                natural,
                diagnostic.tacticalPosition(),
                seedStart,
                seedStart + seedCount - 1L,
                seedCount,
                diagnostic.effectiveness(),
                diagnostic.collective(),
                avg.goalsFor(),
                avg.goalsAgainst(),
                avg.goalDiff(),
                avg.shotsFor(),
                avg.shotsAgainst(),
                avg.possessionFor(),
                avg.xgFor(),
                avg.xgAgainst(),
                avg.xgDiff(),
                avg.centralShotsFor(),
                avg.wideShotsFor(),
                avg.longShotsFor(),
                avg.centralXgFor(),
                avg.wideXgFor(),
                avg.longXgFor()));
        }
        return rows;
    }

    private PositionPixelMatrixSummaryRow executePositionPixelMatrixSummary(
            CareerSave career,
            String matchId,
            String playerId,
            Double requestedTargetXPercent,
            Double requestedTargetYPercent,
            Double deltaXPercent,
            Double deltaYPercent,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Match not found in current tournament: " + matchId));
        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalStateException("SessionTeam not found for match " + matchId);
        }
        String controlledTeamId = resolveControlledTeamId(career, fixture, controlledTeamSide);
        boolean userIsHome = fixture.getHomeTeamId().equals(controlledTeamId);
        boolean userIsAway = fixture.getAwayTeamId().equals(controlledTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException("Position pixel matrix controlled team is not part of match: " + controlledTeamId);
        }
        TeamStyle homeStyle = home.getStyle() != null ? home.getStyle() : TeamStyle.BALANCED;
        TeamStyle awayStyle = away.getStyle() != null ? away.getStyle() : TeamStyle.BALANCED;

        V24MatchContext baseContext = v24ContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            homeStyle,
            awayStyle,
            seedStart);
        List<SessionPlayer> userStarters = userIsHome ? baseContext.homeStartingPlayers() : baseContext.awayStartingPlayers();
        SessionPlayer player = resolvePositionPixelPlayer(userStarters, playerId)
            .orElseThrow(() -> new IllegalArgumentException("playerId '" + playerId + "' not in user starting XI"));
        String resolvedPlayerId = player.getSessionPlayerId();
        LineupSlotDTO baseSlot = (userIsHome ? baseContext.homeSlotsByPlayerId() : baseContext.awaySlotsByPlayerId()).get(resolvedPlayerId);
        String slotId = baseSlot != null ? baseSlot.subdivisionId() : fallbackSubdivision(player.getPosition());
        double fromX = baseSlot != null && baseSlot.customXPercent() != null
            ? baseSlot.customXPercent()
            : canonicalXPercent(slotId).orElse(50.0);
        double fromY = baseSlot != null && baseSlot.customYPercent() != null
            ? baseSlot.customYPercent()
            : canonicalYPercent(slotId).orElse(fallbackYPercent(player.getPosition()));
        double targetXPercent = deltaXPercent != null
            ? clampPercent(fromX + deltaXPercent)
            : requestedTargetXPercent;
        double targetYPercent = deltaYPercent != null
            ? clampPercent(fromY + deltaYPercent)
            : requestedTargetYPercent;
        PositionPixelPlayerDiagnostic baselinePlayerDiagnostic =
            positionPixelPlayerDiagnostic(player, slotId, fromX, fromY);
        PositionPixelPlayerDiagnostic movedPlayerDiagnostic =
            positionPixelPlayerDiagnostic(player, slotId, targetXPercent, targetYPercent);

        SwapAccumulator baseline = new SwapAccumulator();
        SwapAccumulator moved = new SwapAccumulator();
        for (int i = 0; i < seedCount; i++) {
            long seed = seedStart + i;
            V24MatchContext seededBase = v24ContextFactory.buildWithStyles(
                career,
                fixture,
                home,
                away,
                homeStyle,
                awayStyle,
                seed);
            V24DetailedMatchResult baselineResult =
                new V24DetailedMatchEngine().simulate(seededBase, new Random(seed));
            V24MatchContext movedContext = buildMovedPositionContext(
                seededBase,
                controlledTeamId,
                resolvedPlayerId,
                slotId,
                targetXPercent,
                targetYPercent);
            V24DetailedMatchResult movedResult =
                new V24DetailedMatchEngine().simulate(movedContext, new Random(seed));
            baseline.add(baselineResult, userIsHome);
            moved.add(movedResult, userIsHome);
        }

        SwapAverages baseAvg = baseline.averages();
        SwapAverages movedAvg = moved.averages();
        return new PositionPixelMatrixSummaryRow(
            matchId,
            currentFormation(career, controlledTeamId, userIsHome ? home : away),
            resolvedPlayerId,
            safeName(player),
            player.getPosition(),
            slotId,
            round2(fromX),
            round2(fromY),
            round2(targetXPercent),
            round2(targetYPercent),
            seedStart,
            seedStart + seedCount - 1L,
            seedCount,
            baseAvg.goalsFor(), baseAvg.goalsAgainst(), baseAvg.goalDiff(),
            baseAvg.shotsFor(), baseAvg.shotsAgainst(), baseAvg.possessionFor(),
            baseAvg.xgFor(), baseAvg.xgAgainst(), baseAvg.xgDiff(),
            baseAvg.centralShotsFor(), baseAvg.wideShotsFor(), baseAvg.longShotsFor(),
            baseAvg.centralShotsAgainst(), baseAvg.wideShotsAgainst(), baseAvg.longShotsAgainst(),
            baseAvg.centralXgFor(), baseAvg.wideXgFor(), baseAvg.longXgFor(),
            baseAvg.centralXgAgainst(), baseAvg.wideXgAgainst(), baseAvg.longXgAgainst(),
            movedAvg.goalsFor(), movedAvg.goalsAgainst(), movedAvg.goalDiff(),
            movedAvg.shotsFor(), movedAvg.shotsAgainst(), movedAvg.possessionFor(),
            movedAvg.xgFor(), movedAvg.xgAgainst(), movedAvg.xgDiff(),
            movedAvg.centralShotsFor(), movedAvg.wideShotsFor(), movedAvg.longShotsFor(),
            movedAvg.centralShotsAgainst(), movedAvg.wideShotsAgainst(), movedAvg.longShotsAgainst(),
            movedAvg.centralXgFor(), movedAvg.wideXgFor(), movedAvg.longXgFor(),
            movedAvg.centralXgAgainst(), movedAvg.wideXgAgainst(), movedAvg.longXgAgainst(),
            round2(movedAvg.goalsFor() - baseAvg.goalsFor()),
            round2(movedAvg.goalsAgainst() - baseAvg.goalsAgainst()),
            round2(movedAvg.goalDiff() - baseAvg.goalDiff()),
            round2(movedAvg.shotsFor() - baseAvg.shotsFor()),
            round2(movedAvg.shotsAgainst() - baseAvg.shotsAgainst()),
            round2(movedAvg.possessionFor() - baseAvg.possessionFor()),
            round3(movedAvg.xgFor() - baseAvg.xgFor()),
            round3(movedAvg.xgAgainst() - baseAvg.xgAgainst()),
            round3(movedAvg.xgDiff() - baseAvg.xgDiff()),
            round2(movedAvg.centralShotsFor() - baseAvg.centralShotsFor()),
            round2(movedAvg.wideShotsFor() - baseAvg.wideShotsFor()),
            round2(movedAvg.longShotsFor() - baseAvg.longShotsFor()),
            round2(movedAvg.centralShotsAgainst() - baseAvg.centralShotsAgainst()),
            round2(movedAvg.wideShotsAgainst() - baseAvg.wideShotsAgainst()),
            round2(movedAvg.longShotsAgainst() - baseAvg.longShotsAgainst()),
            round3(movedAvg.centralXgFor() - baseAvg.centralXgFor()),
            round3(movedAvg.wideXgFor() - baseAvg.wideXgFor()),
            round3(movedAvg.longXgFor() - baseAvg.longXgFor()),
            round2(movedAvg.leftWideShotsFor() - baseAvg.leftWideShotsFor()),
            round2(movedAvg.rightWideShotsFor() - baseAvg.rightWideShotsFor()),
            round3(movedAvg.leftWideXgFor() - baseAvg.leftWideXgFor()),
            round3(movedAvg.rightWideXgFor() - baseAvg.rightWideXgFor()),
            round3(movedAvg.centralXgAgainst() - baseAvg.centralXgAgainst()),
            round3(movedAvg.wideXgAgainst() - baseAvg.wideXgAgainst()),
            round3(movedAvg.longXgAgainst() - baseAvg.longXgAgainst()),
            round2(movedAvg.leftWideShotsAgainst() - baseAvg.leftWideShotsAgainst()),
            round2(movedAvg.rightWideShotsAgainst() - baseAvg.rightWideShotsAgainst()),
            round3(movedAvg.leftWideXgAgainst() - baseAvg.leftWideXgAgainst()),
            round3(movedAvg.rightWideXgAgainst() - baseAvg.rightWideXgAgainst()),
            baselinePlayerDiagnostic.tacticalPosition(),
            movedPlayerDiagnostic.tacticalPosition(),
            baselinePlayerDiagnostic.effectiveness(),
            movedPlayerDiagnostic.effectiveness(),
            round3(movedPlayerDiagnostic.effectiveness() - baselinePlayerDiagnostic.effectiveness()),
            baselinePlayerDiagnostic.collective(),
            movedPlayerDiagnostic.collective(),
            round2(movedPlayerDiagnostic.collective() - baselinePlayerDiagnostic.collective()));
    }

    private PositionPixelPlayerDiagnostic positionPixelPlayerDiagnostic(
            SessionPlayer player,
            String slotId,
            double xPercent,
            double yPercent) {
        String natural = safePosition(player.getPosition());
        ResolvedDiagnosticSlot slot = new ResolvedDiagnosticSlot(slotId, null, xPercent, yPercent, "pixel-test");
        String tactical = tacticalPositionForDiagnostic(slot, natural);
        double effectiveness = com.footballmanager.domain.model.valueobject.SubdivisionEffectivenessCalculator
            .effectiveness(natural, xPercent, yPercent, tactical);
        int attack = intOr(player.getAttack(), 50);
        int defense = intOr(player.getDefense(), 50);
        int mentality = intOr(player.getMentality(), 50);
        double baseCollective = "GK".equals(natural)
            ? ((defense + mentality) / 2.0)
            : ((attack + defense + mentality) / 3.0);
        return new PositionPixelPlayerDiagnostic(
            tactical,
            round3(effectiveness),
            round2(baseCollective * effectiveness));
    }

    private record PositionPixelPlayerDiagnostic(
        String tacticalPosition,
        double effectiveness,
        double collective
    ) {}

    private List<ScenarioMatrixSummaryRow> executeScenarioMatrixSummary(
            CareerSave career,
            String matchId,
            long seedStart,
            int seedCount,
            String scenarioGroup,
            String controlledTeamSide) {

        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));
        String controlledTeamId = resolveControlledTeamId(career, fixture, controlledTeamSide);
        boolean controlledIsHome = fixture.getHomeTeamId().equals(controlledTeamId);

        Map<String, SummaryAccumulator> accumulators = new LinkedHashMap<>();
        for (int i = 0; i < seedCount; i++) {
            long seed = seedStart + i;
            List<ScenarioMatrixRow> rows = executeScenarioMatrix(career, matchId, seed, scenarioGroup, controlledTeamId);
            Map<String, ScenarioMatrixRow> byScenario = new LinkedHashMap<>();
            for (ScenarioMatrixRow row : rows) {
                byScenario.put(row.scenario(), row);
            }
            for (ScenarioMatrixRow row : rows) {
                if ("base-balanced".equals(row.scenario())
                    || "m45-noop-replay".equals(row.scenario())
                    || "m30-noop-replay".equals(row.scenario())
                    || "m60-noop-replay".equals(row.scenario())) {
                    continue;
                }
                String baselineKey = baselineScenarioFor(row);
                ScenarioMatrixRow baseline = byScenario.get(baselineKey);
                if (baseline == null) {
                    baseline = byScenario.get("base-balanced");
                    baselineKey = "base-balanced";
                }
                if (baseline == null) {
                    continue;
                }
                String finalBaselineKey = baselineKey;
                accumulators
                    .computeIfAbsent(row.scenario(), ignored ->
                        new SummaryAccumulator(row.scenario(), row.actionType(), row.actionDetail(), finalBaselineKey))
                    .add(row, baseline, controlledIsHome);
            }
        }
        return accumulators.values().stream()
            .map(SummaryAccumulator::toRow)
            .toList();
    }

    private String baselineScenarioFor(ScenarioMatrixRow row) {
        if (row.changeMinute() != null && row.changeMinute() >= 60) {
            return "m60-noop-replay";
        }
        if (row.changeMinute() != null && row.changeMinute() >= 45) {
            return "m45-noop-replay";
        }
        if (row.changeMinute() != null && row.changeMinute() >= 30) {
            return "m30-noop-replay";
        }
        return "base-balanced";
    }

    private static final class SummaryAccumulator {
        private final String scenario;
        private final String actionType;
        private final String actionDetail;
        private final String baselineScenario;
        private String baselineFormation;
        private String changedFormation;
        private int count;
        private double sumUserXg;
        private double minUserXg = Double.POSITIVE_INFINITY;
        private double maxUserXg = Double.NEGATIVE_INFINITY;
        private double sumOpponentXg;
        private double sumUserShots;
        private double sumOpponentShots;
        private double sumUserPossession;
        private double sumUserCentral;
        private double sumUserWide;
        private double sumOpponentCentral;
        private double sumOpponentWide;
        private double sumUserCentralXg;
        private double sumUserWideXg;
        private double sumOpponentCentralXg;
        private double sumOpponentWideXg;
        private double sumUserLeftWide;
        private double sumUserRightWide;
        private double sumOpponentLeftWide;
        private double sumOpponentRightWide;
        private double sumUserLeftWideXg;
        private double sumUserRightWideXg;
        private double sumOpponentLeftWideXg;
        private double sumOpponentRightWideXg;

        private SummaryAccumulator(String scenario, String actionType, String actionDetail, String baselineScenario) {
            this.scenario = scenario;
            this.actionType = actionType;
            this.actionDetail = actionDetail;
            this.baselineScenario = baselineScenario;
        }

        private void add(ScenarioMatrixRow row, ScenarioMatrixRow baseline, boolean userIsHome) {
            if (baselineFormation == null || baselineFormation.isBlank()) {
                baselineFormation = baseline.formation();
            }
            if (changedFormation == null || changedFormation.isBlank()) {
                changedFormation = "FORMATION".equals(row.actionType())
                    ? row.actionDetail()
                    : row.formation();
            }
            double userXg = userIsHome ? row.homeXg() : row.awayXg();
            double baseUserXg = userIsHome ? baseline.homeXg() : baseline.awayXg();
            double opponentXg = userIsHome ? row.awayXg() : row.homeXg();
            double baseOpponentXg = userIsHome ? baseline.awayXg() : baseline.homeXg();
            int userShots = userIsHome ? row.homeShots() : row.awayShots();
            int baseUserShots = userIsHome ? baseline.homeShots() : baseline.awayShots();
            int opponentShots = userIsHome ? row.awayShots() : row.homeShots();
            int baseOpponentShots = userIsHome ? baseline.awayShots() : baseline.homeShots();
            int userPossession = userIsHome ? row.homePossession() : row.awayPossession();
            int baseUserPossession = userIsHome ? baseline.homePossession() : baseline.awayPossession();
            int userCentral = userIsHome ? row.homeCentralShots() : row.awayCentralShots();
            int baseUserCentral = userIsHome ? baseline.homeCentralShots() : baseline.awayCentralShots();
            int userWide = userIsHome ? row.homeWideShots() : row.awayWideShots();
            int baseUserWide = userIsHome ? baseline.homeWideShots() : baseline.awayWideShots();
            int opponentCentral = userIsHome ? row.awayCentralShots() : row.homeCentralShots();
            int baseOpponentCentral = userIsHome ? baseline.awayCentralShots() : baseline.homeCentralShots();
            int opponentWide = userIsHome ? row.awayWideShots() : row.homeWideShots();
            int baseOpponentWide = userIsHome ? baseline.awayWideShots() : baseline.homeWideShots();
            double userCentralXg = userIsHome ? row.homeCentralXg() : row.awayCentralXg();
            double baseUserCentralXg = userIsHome ? baseline.homeCentralXg() : baseline.awayCentralXg();
            double userWideXg = userIsHome ? row.homeWideXg() : row.awayWideXg();
            double baseUserWideXg = userIsHome ? baseline.homeWideXg() : baseline.awayWideXg();
            double opponentCentralXg = userIsHome ? row.awayCentralXg() : row.homeCentralXg();
            double baseOpponentCentralXg = userIsHome ? baseline.awayCentralXg() : baseline.homeCentralXg();
            double opponentWideXg = userIsHome ? row.awayWideXg() : row.homeWideXg();
            double baseOpponentWideXg = userIsHome ? baseline.awayWideXg() : baseline.homeWideXg();
            int userLeftWide = userIsHome ? row.homeLeftWideShots() : row.awayLeftWideShots();
            int baseUserLeftWide = userIsHome ? baseline.homeLeftWideShots() : baseline.awayLeftWideShots();
            int userRightWide = userIsHome ? row.homeRightWideShots() : row.awayRightWideShots();
            int baseUserRightWide = userIsHome ? baseline.homeRightWideShots() : baseline.awayRightWideShots();
            int opponentLeftWide = userIsHome ? row.awayLeftWideShots() : row.homeLeftWideShots();
            int baseOpponentLeftWide = userIsHome ? baseline.awayLeftWideShots() : baseline.homeLeftWideShots();
            int opponentRightWide = userIsHome ? row.awayRightWideShots() : row.homeRightWideShots();
            int baseOpponentRightWide = userIsHome ? baseline.awayRightWideShots() : baseline.homeRightWideShots();
            double userLeftWideXg = userIsHome ? row.homeLeftWideXg() : row.awayLeftWideXg();
            double baseUserLeftWideXg = userIsHome ? baseline.homeLeftWideXg() : baseline.awayLeftWideXg();
            double userRightWideXg = userIsHome ? row.homeRightWideXg() : row.awayRightWideXg();
            double baseUserRightWideXg = userIsHome ? baseline.homeRightWideXg() : baseline.awayRightWideXg();
            double opponentLeftWideXg = userIsHome ? row.awayLeftWideXg() : row.homeLeftWideXg();
            double baseOpponentLeftWideXg = userIsHome ? baseline.awayLeftWideXg() : baseline.homeLeftWideXg();
            double opponentRightWideXg = userIsHome ? row.awayRightWideXg() : row.homeRightWideXg();
            double baseOpponentRightWideXg = userIsHome ? baseline.awayRightWideXg() : baseline.homeRightWideXg();

            double userXgDelta = userXg - baseUserXg;
            count++;
            sumUserXg += userXgDelta;
            minUserXg = Math.min(minUserXg, userXgDelta);
            maxUserXg = Math.max(maxUserXg, userXgDelta);
            sumOpponentXg += opponentXg - baseOpponentXg;
            sumUserShots += userShots - baseUserShots;
            sumOpponentShots += opponentShots - baseOpponentShots;
            sumUserPossession += userPossession - baseUserPossession;
            sumUserCentral += userCentral - baseUserCentral;
            sumUserWide += userWide - baseUserWide;
            sumOpponentCentral += opponentCentral - baseOpponentCentral;
            sumOpponentWide += opponentWide - baseOpponentWide;
            sumUserCentralXg += userCentralXg - baseUserCentralXg;
            sumUserWideXg += userWideXg - baseUserWideXg;
            sumOpponentCentralXg += opponentCentralXg - baseOpponentCentralXg;
            sumOpponentWideXg += opponentWideXg - baseOpponentWideXg;
            sumUserLeftWide += userLeftWide - baseUserLeftWide;
            sumUserRightWide += userRightWide - baseUserRightWide;
            sumOpponentLeftWide += opponentLeftWide - baseOpponentLeftWide;
            sumOpponentRightWide += opponentRightWide - baseOpponentRightWide;
            sumUserLeftWideXg += userLeftWideXg - baseUserLeftWideXg;
            sumUserRightWideXg += userRightWideXg - baseUserRightWideXg;
            sumOpponentLeftWideXg += opponentLeftWideXg - baseOpponentLeftWideXg;
            sumOpponentRightWideXg += opponentRightWideXg - baseOpponentRightWideXg;
        }

        private ScenarioMatrixSummaryRow toRow() {
            return new ScenarioMatrixSummaryRow(
                scenario,
                actionType,
                actionDetail,
                count,
                round3(sumUserXg / count),
                round3(minUserXg),
                round3(maxUserXg),
                round3(sumOpponentXg / count),
                round2(sumUserShots / count),
                round2(sumOpponentShots / count),
                round2(sumUserPossession / count),
                round2(sumUserCentral / count),
                round2(sumUserWide / count),
                round2(sumOpponentCentral / count),
                round2(sumOpponentWide / count),
                round3(sumUserCentralXg / count),
                round3(sumUserWideXg / count),
                round3(sumOpponentCentralXg / count),
                round3(sumOpponentWideXg / count),
                round2(sumUserLeftWide / count),
                round2(sumUserRightWide / count),
                round2(sumOpponentLeftWide / count),
                round2(sumOpponentRightWide / count),
                round3(sumUserLeftWideXg / count),
                round3(sumUserRightWideXg / count),
                round3(sumOpponentLeftWideXg / count),
                round3(sumOpponentRightWideXg / count),
                baselineScenario,
                baselineFormation,
                changedFormation,
                normalizedFormation(baselineFormation).equals(normalizedFormation(changedFormation)));
        }

        private static String normalizedFormation(String formation) {
            return formation == null ? "" : formation.trim().toUpperCase(Locale.ROOT);
        }

        private static double round3(double value) {
            return Math.round(value * 1000.0) / 1000.0;
        }

        private static double round2(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
    }

    private List<ScenarioMatrixRow> executeScenarioMatrix(CareerSave career, String matchId, long seed) {
        return executeScenarioMatrix(career, matchId, seed, null);
    }

    private List<ScenarioMatrixRow> executeScenarioMatrix(
            CareerSave career,
            String matchId,
            long seed,
            String scenarioGroup) {
        return executeScenarioMatrix(career, matchId, seed, scenarioGroup, null);
    }

    private List<ScenarioMatrixRow> executeScenarioMatrix(
            CareerSave career,
            String matchId,
            long seed,
            String scenarioGroup,
            String controlledTeamIdOverride) {
        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));

        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalStateException(
                "SessionTeam not found for match " + matchId
                    + " (home=" + fixture.getHomeTeamId()
                    + ", away=" + fixture.getAwayTeamId() + ")");
        }

        String userTeamId = controlledTeamIdOverride != null && !controlledTeamIdOverride.isBlank()
            ? controlledTeamIdOverride
            : career.getUserSessionTeamId();
        boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
        boolean userIsAway = fixture.getAwayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException(
                "Scenario matrix requires the controlled team to play this match: " + userTeamId);
        }

        String formation = currentFormation(career, userTeamId, userIsHome ? home : away);
        String normalizedScenarioGroup = normalizeScenarioGroup(scenarioGroup);
        List<ScenarioMatrixRow> rows = new ArrayList<>();
        rows.add(runScenario(career, fixture, home, away, userTeamId, seed,
            "base-balanced", "Base: full match BALANCED", formation,
            TeamStyle.BALANCED, null, ScenarioAction.none()));
        rows.add(runScenario(career, fixture, home, away, userTeamId, seed,
            "m45-noop-replay", "Minute 45 -> replay without tactical change", formation,
            TeamStyle.BALANCED, 45, ScenarioAction.noopReplay()));
        addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-wide", "Minute 45 -> WIDE_PLAY", formation,
            TeamStyle.BALANCED, 45, ScenarioAction.style(TeamStyle.WIDE_PLAY));
        addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-left", "Minute 45 -> LEFT_FLANK", formation,
            TeamStyle.BALANCED, 45, ScenarioAction.style(TeamStyle.LEFT_FLANK));
        addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-right", "Minute 45 -> RIGHT_FLANK", formation,
            TeamStyle.BALANCED, 45, ScenarioAction.style(TeamStyle.RIGHT_FLANK));
        addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-central", "Minute 45 -> CENTRAL_PLAY", formation,
            TeamStyle.BALANCED, 45, ScenarioAction.style(TeamStyle.CENTRAL_PLAY));
        addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-opponent-wide",
            "Minute 45 -> opponent WIDE_PLAY (defensive channel exposure)",
            formation,
            TeamStyle.BALANCED,
            45,
            ScenarioAction.opponentStyle(TeamStyle.WIDE_PLAY));
        addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-opponent-left",
            "Minute 45 -> opponent LEFT_FLANK (left defensive channel exposure)",
            formation,
            TeamStyle.BALANCED,
            45,
            ScenarioAction.opponentStyle(TeamStyle.LEFT_FLANK));
        addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-opponent-right",
            "Minute 45 -> opponent RIGHT_FLANK (right defensive channel exposure)",
            formation,
            TeamStyle.BALANCED,
            45,
            ScenarioAction.opponentStyle(TeamStyle.RIGHT_FLANK));
        addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-opponent-central",
            "Minute 45 -> opponent CENTRAL_PLAY (central defensive exposure)",
            formation,
            TeamStyle.BALANCED,
            45,
            ScenarioAction.opponentStyle(TeamStyle.CENTRAL_PLAY));
        V24MatchContext baseContext = v24ContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            userIsHome ? TeamStyle.BALANCED : home.getStyle(),
            userIsHome ? away.getStyle() : TeamStyle.BALANCED,
            seed);
        List<SessionPlayer> userStarters = userIsHome
            ? baseContext.homeStartingPlayers()
            : baseContext.awayStartingPlayers();
        buildFormationScenarioAction(userStarters, "4-4-2")
            .ifPresent(action -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
                "m45-formation-442", "Minute 45 -> formation 4-4-2 with visual slots", formation,
                TeamStyle.BALANCED, 45, action));
        buildFormationScenarioAction(userStarters, "4-3-3")
            .ifPresent(action -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
                "m45-formation-433", "Minute 45 -> formation 4-3-3 with visual slots", formation,
                TeamStyle.BALANCED, 45, action));
        buildFormationScenarioAction(userStarters, "4-2-3-1")
            .ifPresent(action -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
                "m45-formation-4231", "Minute 45 -> formation 4-2-3-1 with visual slots", formation,
                TeamStyle.BALANCED, 45, action));
        Optional<PositionPlan> advancedMid = chooseMidfielderPositionPlan(baseContext, userTeamId, 50.0, 40.0);
        advancedMid.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-position-mid-up",
            "Minute 45 -> move " + plan.playerName() + " to x50/y40",
            formation,
            TeamStyle.BALANCED,
            45,
            ScenarioAction.position(plan)));
        Optional<PositionPlan> advancedMidOneMore = chooseMidfielderPositionPlan(baseContext, userTeamId, 50.0, 39.0);
        advancedMidOneMore.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-position-mid-up-1px",
            "Minute 45 -> move " + plan.playerName() + " to x50/y39",
            formation,
            TeamStyle.BALANCED,
            45,
            ScenarioAction.position(plan)));
        Optional<PositionPlan> wideMid = chooseMidfielderPositionPlan(baseContext, userTeamId, 18.0, 50.0);
        wideMid.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-position-mid-wide",
            "Minute 45 -> move " + plan.playerName() + " wide x18/y50",
            formation,
            TeamStyle.BALANCED,
            45,
            ScenarioAction.position(plan)));

        Optional<PositionPlan> compactCenterPlan =
            buildShapePlan(baseContext, userTeamId, formation, "compact-center", ShapePreset.COMPACT_CENTER);
        Optional<PositionPlan> wideOverloadPlan =
            buildShapePlan(baseContext, userTeamId, formation, "wide-overload", ShapePreset.WIDE_OVERLOAD);
        Optional<PositionPlan> attackingStepPlan =
            buildShapePlan(baseContext, userTeamId, formation, "attacking-step", ShapePreset.ATTACKING_STEP);
        Optional<PositionPlan> attackingHighPlan =
            buildShapePlan(baseContext, userTeamId, formation, "attacking-high", ShapePreset.ATTACKING_HIGH);
        Optional<PositionPlan> highPressPlan =
            buildShapePlan(baseContext, userTeamId, formation, "high-press", ShapePreset.HIGH_PRESS);
        Optional<PositionPlan> doubleStrikerPlan =
            buildShapePlan(baseContext, userTeamId, formation, "double-striker", ShapePreset.DOUBLE_STRIKER);
        Optional<PositionPlan> allOutPlan =
            buildShapePlan(baseContext, userTeamId, formation, "all-out", ShapePreset.ALL_OUT);
        Optional<PositionPlan> defensiveStepPlan =
            buildShapePlan(baseContext, userTeamId, formation, "defensive-step", ShapePreset.DEFENSIVE_STEP);
        Optional<PositionPlan> defensiveLowPlan =
            buildShapePlan(baseContext, userTeamId, formation, "defensive-low", ShapePreset.DEFENSIVE_LOW);
        Optional<PositionPlan> leftOverloadPlan =
            buildShapePlan(baseContext, userTeamId, formation, "left-overload", ShapePreset.LEFT_OVERLOAD);
        Optional<PositionPlan> rightOverloadPlan =
            buildShapePlan(baseContext, userTeamId, formation, "right-overload", ShapePreset.RIGHT_OVERLOAD);

        List.of(
            compactCenterPlan,
            wideOverloadPlan,
            attackingStepPlan,
            attackingHighPlan,
            highPressPlan,
            doubleStrikerPlan,
            allOutPlan,
            defensiveStepPlan,
            defensiveLowPlan,
            leftOverloadPlan,
            rightOverloadPlan
        ).forEach(planOpt -> planOpt.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup,
            career,
            fixture,
            home,
            away,
            userTeamId,
            seed,
            "m45-shape-" + plan.playerId(),
            "Minute 45 -> manual shape " + plan.playerName(),
            formation,
            TeamStyle.BALANCED,
            45,
            ScenarioAction.position(plan))));

        Optional<SubPlan> impactSub = chooseImpactSubstitution(career, fixture, userTeamId, home, away);
        Optional<SubPlan> upgradeSub = chooseScoredSubstitution(career, fixture, userTeamId, home, away, true);
        Optional<SubPlan> downgradeSub = chooseScoredSubstitution(career, fixture, userTeamId, home, away, false);
        Optional<SubPlan> offensiveUpgradeSub = chooseScoredSubstitution(
            career, fixture, userTeamId, home, away, true, Set.of("ATT", "WINGER"));
        Optional<SubPlan> offensiveDowngradeSub = chooseScoredSubstitution(
            career, fixture, userTeamId, home, away, false, Set.of("ATT", "WINGER"));
        Optional<SubPlan> defensiveUpgradeSub = chooseScoredSubstitution(
            career, fixture, userTeamId, home, away, true, Set.of("DEF"));
        Optional<SubPlan> defensiveDowngradeSub = chooseScoredSubstitution(
            career, fixture, userTeamId, home, away, false, Set.of("DEF"));

        impactSub.ifPresent(sub -> {
            highPressPlan.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
                "m45-combo-high-press-impact-sub",
                "Minute 45 -> high press + impact substitution",
                formation,
                TeamStyle.BALANCED,
                45,
                ScenarioAction.positionAndSubstitution(plan, sub)));
            doubleStrikerPlan.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
                "m45-combo-double-striker-impact-sub",
                "Minute 45 -> double striker + impact substitution",
                formation,
                TeamStyle.BALANCED,
                45,
                ScenarioAction.positionAndSubstitution(plan, sub)));
            allOutPlan.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
                "m45-combo-all-out-impact-sub",
                "Minute 45 -> all out + impact substitution",
                formation,
                TeamStyle.BALANCED,
                45,
                ScenarioAction.positionAndSubstitution(plan, sub)));
        });
        boolean hasMinute30Scenario = offensiveUpgradeSub.isPresent()
            || offensiveDowngradeSub.isPresent()
            || defensiveUpgradeSub.isPresent()
            || defensiveDowngradeSub.isPresent();
        if (hasMinute30Scenario) {
            rows.add(runScenario(career, fixture, home, away, userTeamId, seed,
            "m30-noop-replay",
            "Minute 30 -> replay without tactical change",
            formation,
            TeamStyle.BALANCED,
            30,
            ScenarioAction.noopReplay()));
        }
        offensiveUpgradeSub.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m30-offensive-upgrade-sub",
            "Minute 30 lab -> offensive upgrade " + plan.offName() + " out, " + plan.onName() + " in",
            formation,
            TeamStyle.BALANCED,
            30,
            ScenarioAction.substitution(plan)));
        offensiveDowngradeSub.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m30-offensive-downgrade-sub",
            "Minute 30 lab -> offensive downgrade " + plan.offName() + " out, " + plan.onName() + " in",
            formation,
            TeamStyle.BALANCED,
            30,
            ScenarioAction.substitution(plan)));
        defensiveUpgradeSub.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m30-defensive-upgrade-sub",
            "Minute 30 lab -> defensive upgrade " + plan.offName() + " out, " + plan.onName() + " in",
            formation,
            TeamStyle.BALANCED,
            30,
            ScenarioAction.substitution(plan)));
        defensiveDowngradeSub.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m30-defensive-downgrade-sub",
            "Minute 30 lab -> defensive downgrade " + plan.offName() + " out, " + plan.onName() + " in",
            formation,
            TeamStyle.BALANCED,
            30,
            ScenarioAction.substitution(plan)));
        boolean hasMinute60Scenario = impactSub.isPresent()
            || upgradeSub.isPresent()
            || downgradeSub.isPresent()
            || offensiveUpgradeSub.isPresent()
            || offensiveDowngradeSub.isPresent()
            || defensiveUpgradeSub.isPresent()
            || defensiveDowngradeSub.isPresent();
        if (hasMinute60Scenario) {
            rows.add(runScenario(career, fixture, home, away, userTeamId, seed,
            "m60-noop-replay",
            "Minute 60 -> replay without tactical change",
            formation,
            TeamStyle.BALANCED,
            60,
            ScenarioAction.noopReplay()));
        }
        impactSub.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m60-impact-sub",
            "Minute 60 -> " + plan.offName() + " out, " + plan.onName() + " in",
            formation,
            TeamStyle.BALANCED,
            60,
            ScenarioAction.substitution(plan)));
        upgradeSub.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m60-upgrade-sub",
            "Minute 60 -> upgrade " + plan.offName() + " out, " + plan.onName() + " in",
            formation,
            TeamStyle.BALANCED,
            60,
            ScenarioAction.substitution(plan)));
        downgradeSub.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m60-downgrade-sub",
            "Minute 60 -> downgrade " + plan.offName() + " out, " + plan.onName() + " in",
            formation,
            TeamStyle.BALANCED,
            60,
            ScenarioAction.substitution(plan)));
        offensiveUpgradeSub.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m60-offensive-upgrade-sub",
            "Minute 60 -> offensive upgrade " + plan.offName() + " out, " + plan.onName() + " in",
            formation,
            TeamStyle.BALANCED,
            60,
            ScenarioAction.substitution(plan)));
        offensiveDowngradeSub.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m60-offensive-downgrade-sub",
            "Minute 60 -> offensive downgrade " + plan.offName() + " out, " + plan.onName() + " in",
            formation,
            TeamStyle.BALANCED,
            60,
            ScenarioAction.substitution(plan)));
        defensiveUpgradeSub.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m60-defensive-upgrade-sub",
            "Minute 60 -> defensive upgrade " + plan.offName() + " out, " + plan.onName() + " in",
            formation,
            TeamStyle.BALANCED,
            60,
            ScenarioAction.substitution(plan)));
        defensiveDowngradeSub.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m60-defensive-downgrade-sub",
            "Minute 60 -> defensive downgrade " + plan.offName() + " out, " + plan.onName() + " in",
            formation,
            TeamStyle.BALANCED,
            60,
            ScenarioAction.substitution(plan)));
        return rows;
    }

    private List<ScenarioMatrixRow> filterScenarioRows(List<ScenarioMatrixRow> rows, String scenarioGroup) {
        String group = normalizeScenarioGroup(scenarioGroup);
        if (group.isBlank() || "ALL".equals(group)) {
            return rows;
        }
        Set<String> baselines = Set.of("base-balanced", "m30-noop-replay", "m45-noop-replay", "m60-noop-replay");
        return rows.stream()
            .filter(row -> baselines.contains(row.scenario()) || scenarioMatchesGroup(row.scenario(), group))
            .toList();
    }

    private boolean scenarioMatchesGroup(String scenario, String group) {
        if (scenario == null) {
            return false;
        }
        String key = scenario.toLowerCase(Locale.ROOT);
        return switch (group) {
            case "OPPONENT" -> key.startsWith("m45-opponent-");
            case "OFFENSE" -> !key.startsWith("m45-opponent-") && (key.contains("wide")
                || key.contains("left")
                || key.contains("right")
                || key.contains("central")
                || key.contains("formation")
                || key.contains("position")
                || key.contains("attacking")
                || key.contains("press")
                || key.contains("striker")
                || key.contains("all-out")
                || key.contains("combo")
                || key.contains("compact")
                || key.contains("offensive"));
            case "DEFENSE" -> key.contains("defensive")
                || key.contains("defense")
                || key.contains("low");
            default -> true;
        };
    }

    private String normalizeScenarioGroup(String scenarioGroup) {
        return scenarioGroup == null ? "" : scenarioGroup.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveControlledTeamId(CareerSave career, MatchFixture fixture, String controlledTeamSide) {
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
                        "Scenario matrix summary USER side requires a match involving the user team: " + userTeamId);
                }
                yield userTeamId;
            }
            default -> throw new IllegalArgumentException(
                "controlledTeamSide must be USER, HOME or AWAY");
        };
    }

    private void addScenarioIfRequested(
            List<ScenarioMatrixRow> rows,
            String normalizedScenarioGroup,
            CareerSave career,
            MatchFixture fixture,
            SessionTeam home,
            SessionTeam away,
            String userTeamId,
            long seed,
            String scenario,
            String description,
            String formation,
            TeamStyle baseUserStyle,
            Integer changeMinute,
            ScenarioAction action) {
        if (!normalizedScenarioGroup.isBlank()
            && !"ALL".equals(normalizedScenarioGroup)
            && !scenarioMatchesGroup(scenario, normalizedScenarioGroup)) {
            return;
        }
        rows.add(runScenario(career, fixture, home, away, userTeamId, seed,
            scenario, description, formation, baseUserStyle, changeMinute, action));
    }

    private Optional<ScenarioAction> buildFormationScenarioAction(
            List<SessionPlayer> starters,
            String formationName) {
        if (starters == null || starters.size() != 11 || formationName == null || formationName.isBlank()) {
            return Optional.empty();
        }
        return formationService.getAllFormations().stream()
            .filter(formation -> formationName.equals(formation.name()))
            .findFirst()
            .map(formation -> ScenarioAction.formation(
                formation.name(),
                buildFormationMatrixSlots(starters, formation)));
    }

    private ScenarioMatrixRow runScenario(
            CareerSave career,
            MatchFixture fixture,
            SessionTeam home,
            SessionTeam away,
            String userTeamId,
            long seed,
            String scenario,
            String description,
            String formation,
            TeamStyle initialStyle,
            Integer changeMinute,
            ScenarioAction action) {

        boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
        TeamStyle homeStyle = userIsHome ? initialStyle : home.getStyle();
        TeamStyle awayStyle = userIsHome ? away.getStyle() : initialStyle;
        ScenarioAction safeAction = action != null ? action : ScenarioAction.none();

        V24MatchContext context = v24ContextFactory.buildWithStyles(
            career, fixture, home, away, homeStyle, awayStyle, seed);

        V24DetailedMatchResult result;
        long tacticalChanges = 0;
        long substitutions = 0;
        if (changeMinute == null || safeAction.type() == ScenarioActionType.NONE) {
            result = new V24DetailedMatchEngine().simulate(context, new Random(seed));
        } else {
            V24LiveSession session = new V24LiveSession(context, seed);
            for (int i = 0; i < changeMinute; i++) {
                session.tick();
            }
            if (safeAction.type() == ScenarioActionType.STYLE) {
                session.mutateContext(ctx -> ctx.withNewStyle(userTeamId, safeAction.changedStyle()));
                tacticalChanges = 1;
            } else if (safeAction.type() == ScenarioActionType.OPPONENT_STYLE) {
                String opponentTeamId = userIsHome ? fixture.getAwayTeamId() : fixture.getHomeTeamId();
                session.mutateContext(ctx -> ctx.withNewStyle(opponentTeamId, safeAction.changedStyle()));
                tacticalChanges = 1;
            } else if (safeAction.type() == ScenarioActionType.NOOP_REPLAY) {
                session.mutateContext(ctx -> ctx);
            } else if (safeAction.type() == ScenarioActionType.FORMATION) {
                session.mutateContext(ctx -> {
                    V24MatchContext changed = ctx.withNewFormation(userTeamId, safeAction.changedFormation());
                    return safeAction.formationSlotsByPlayerId() != null
                        && !safeAction.formationSlotsByPlayerId().isEmpty()
                            ? changed.withSlots(userTeamId, safeAction.formationSlotsByPlayerId())
                            : changed;
                });
                tacticalChanges = 1;
            } else if (safeAction.type() == ScenarioActionType.POSITION && safeAction.positionPlan() != null) {
                PositionPlan plan = safeAction.positionPlan();
                session.mutateContext(ctx -> ctx.withSlots(userTeamId, plan.slotsByPlayerId()));
                tacticalChanges = 1;
            } else if (safeAction.type() == ScenarioActionType.SUBSTITUTION && safeAction.subPlan() != null) {
                SubPlan plan = safeAction.subPlan();
                session.mutateContext(ctx -> ctx.withManualSubstitution(
                    userTeamId,
                    plan.playerOffId(),
                    plan.playerOnId(),
                    changeMinute));
                substitutions = 1;
            } else if (safeAction.type() == ScenarioActionType.POSITION_AND_SUBSTITUTION
                && safeAction.positionPlan() != null
                && safeAction.subPlan() != null) {
                PositionPlan positionPlan = safeAction.positionPlan();
                SubPlan subPlan = safeAction.subPlan();
                session.mutateContext(ctx -> ctx
                    .withSlots(userTeamId, positionPlan.slotsByPlayerId())
                    .withManualSubstitution(
                        userTeamId,
                        subPlan.playerOffId(),
                        subPlan.playerOnId(),
                        changeMinute));
                tacticalChanges = 1;
                substitutions = 1;
            }
            while (!session.isFinished()) {
                session.tick();
            }
            result = session.finalResult();
        }

        ZoneCounts zones = countZones(result);
        return new ScenarioMatrixRow(
            scenario,
            description,
            formation,
            initialStyle,
            changeMinute,
            safeAction.changedStyle(),
            safeAction.type().name(),
            safeAction.detail(),
            result.homeGoals(),
            result.awayGoals(),
            result.homeXg(),
            result.awayXg(),
            result.homeShots(),
            result.awayShots(),
            result.homePossession(),
            result.awayPossession(),
            zones.homeCentral(),
            zones.homeWide(),
            zones.homeLong(),
            zones.awayCentral(),
            zones.awayWide(),
            zones.awayLong(),
            zones.homeCentralXg(),
            zones.homeWideXg(),
            zones.homeLongXg(),
            zones.homeLeftWide(),
            zones.homeRightWide(),
            zones.homeLeftWideXg(),
            zones.homeRightWideXg(),
            zones.awayCentralXg(),
            zones.awayWideXg(),
            zones.awayLongXg(),
            zones.awayLeftWide(),
            zones.awayRightWide(),
            zones.awayLeftWideXg(),
            zones.awayRightWideXg(),
            tacticalChanges,
            substitutions
        );
    }

    private Optional<PositionPlan> chooseMidfielderPositionPlan(
            V24MatchContext context,
            String userTeamId,
            double xPercent,
            double yPercent) {

        boolean userIsHome = context.homeTeamId().equals(userTeamId);
        boolean userIsAway = context.awayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            return Optional.empty();
        }

        List<SessionPlayer> starters = userIsHome
            ? context.homeStartingPlayers()
            : context.awayStartingPlayers();
        Map<String, LineupSlotDTO> baseSlots = userIsHome
            ? context.homeSlotsByPlayerId()
            : context.awaySlotsByPlayerId();

        return starters.stream()
            .filter(p -> p != null
                && p.getSessionPlayerId() != null
                && "MID".equals(p.getPosition()))
            .findFirst()
            .map(player -> {
                String playerId = player.getSessionPlayerId();
                LineupSlotDTO previous = baseSlots.get(playerId);
                Map<String, LineupSlotDTO> moved = new LinkedHashMap<>(baseSlots);
                moved.put(playerId, new LineupSlotDTO(
                    playerId,
                    previous != null ? previous.subdivisionId() : "S5-2",
                    xPercent,
                    yPercent));
                return new PositionPlan(
                    playerId,
                    safeName(player),
                    xPercent,
                    yPercent,
                    moved);
            });
    }

    private Optional<PositionPlan> buildShapePlan(
            V24MatchContext context,
            String userTeamId,
            String formation,
            String shapeName,
            ShapePreset preset) {

        boolean userIsHome = context.homeTeamId().equals(userTeamId);
        boolean userIsAway = context.awayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            return Optional.empty();
        }

        List<SessionPlayer> starters = userIsHome
            ? context.homeStartingPlayers()
            : context.awayStartingPlayers();
        Map<String, LineupSlotDTO> baseSlots = userIsHome
            ? context.homeSlotsByPlayerId()
            : context.awaySlotsByPlayerId();
        if (starters == null || starters.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Integer> positionIndex = new HashMap<>();
        Map<String, LineupSlotDTO> moved = new LinkedHashMap<>(baseSlots);
        Map<String, double[]> formationCoords = formationService.getCoordsByFormation(formation);
        for (SessionPlayer player : starters) {
            if (player == null || player.getSessionPlayerId() == null) continue;
            String position = player.getPosition() != null ? player.getPosition() : "MID";
            int index = positionIndex.merge(position, 1, Integer::sum) - 1;
            LineupSlotDTO previous = baseSlots.get(player.getSessionPlayerId());
            double[] coords = shapeCoords(position, index, preset, previous, formationCoords);
            moved.put(player.getSessionPlayerId(), new LineupSlotDTO(
                player.getSessionPlayerId(),
                previous != null ? previous.subdivisionId() : fallbackSubdivision(position),
                coords[0],
                coords[1]));
        }

        return Optional.of(new PositionPlan(
            shapeName,
            shapeName,
            50.0,
            50.0,
            moved));
    }

    private double[] shapeCoords(
            String position,
            int index,
            ShapePreset preset,
            LineupSlotDTO previous,
            Map<String, double[]> formationCoords) {
        double[] canonical = previous != null && previous.subdivisionId() != null
            ? formationCoords.get(previous.subdivisionId())
            : null;
        if ("GK".equals(position)) {
            return new double[] {
                previous != null && previous.customXPercent() != null ? previous.customXPercent() : canonical != null ? canonical[0] : 50.0,
                previous != null && previous.customYPercent() != null ? previous.customYPercent() : canonical != null ? canonical[1] : 94.0
            };
        }

        double y = previous != null && previous.customYPercent() != null ? previous.customYPercent() : canonical != null ? canonical[1] : switch (position) {
            case "DEF" -> 78.0;
            case "ATT" -> 18.0;
            case "WINGER" -> 30.0;
            default -> 52.0;
        };
        double x = previous != null && previous.customXPercent() != null ? previous.customXPercent() : canonical != null ? canonical[0] : switch (position) {
            case "DEF" -> pick(index, 18.0, 38.0, 62.0, 82.0, 50.0);
            case "ATT" -> pick(index, 42.0, 58.0, 50.0, 35.0, 65.0);
            case "WINGER" -> pick(index, 18.0, 82.0, 30.0, 70.0, 50.0);
            default -> pick(index, 24.0, 42.0, 58.0, 76.0, 50.0);
        };

        switch (preset) {
            case COMPACT_CENTER -> x = 50.0 + ((x - 50.0) * switch (position) {
                case "DEF" -> 0.75;
                case "ATT" -> 0.65;
                case "WINGER" -> 0.55;
                default -> 0.55;
            });
            case WIDE_OVERLOAD -> x = 50.0 + ((x - 50.0) * switch (position) {
                case "DEF" -> 1.00;
                case "ATT" -> 1.03;
                case "WINGER" -> 1.08;
                default -> 1.07;
            });
            case ATTACKING_STEP -> y = Math.max(8.0, y - ("DEF".equals(position) ? 3.0 : 5.0));
            case ATTACKING_HIGH -> y = Math.max(8.0, y - ("DEF".equals(position) ? 3.0 : 6.0));
            case HIGH_PRESS -> y = Math.max(8.0, y - switch (position) {
                case "DEF" -> 7.0;
                case "MID" -> 8.0;
                case "WINGER" -> 7.0;
                case "ATT" -> 4.0;
                default -> 7.0;
            });
            case DOUBLE_STRIKER -> {
                y = switch (position) {
                    case "ATT" -> Math.max(8.0, y - 4.0);
                    case "WINGER" -> Math.max(10.0, y - 9.0);
                    case "MID" -> Math.max(18.0, y - 5.0);
                    case "DEF" -> Math.max(60.0, y - 2.0);
                    default -> Math.max(8.0, y - 4.0);
                };
                if ("WINGER".equals(position) || "ATT".equals(position)) {
                    x = 50.0 + ((x - 50.0) * 0.72);
                }
            }
            case ALL_OUT -> {
                y = Math.max(8.0, y - switch (position) {
                    case "DEF" -> 6.0;
                    case "MID" -> 10.0;
                    case "WINGER" -> 12.0;
                    case "ATT" -> 7.0;
                    default -> 9.0;
                });
                x = 50.0 + ((x - 50.0) * switch (position) {
                    case "DEF" -> 0.88;
                    case "MID" -> 0.82;
                    case "WINGER" -> 0.90;
                    case "ATT" -> 0.75;
                    default -> 0.85;
                });
            }
            case DEFENSIVE_STEP -> y = Math.min(92.0, y + switch (position) {
                case "DEF" -> 10.0;
                case "MID" -> 7.0;
                case "WINGER" -> 5.0;
                case "ATT" -> 4.0;
                default -> 6.0;
            });
            case DEFENSIVE_LOW -> y = Math.min(92.0, y + switch (position) {
                case "DEF" -> 8.0;
                case "MID" -> 8.0;
                case "WINGER" -> 7.0;
                case "ATT" -> 6.0;
                default -> 7.0;
            });
            case LEFT_OVERLOAD -> x = clamp(x - switch (position) {
                case "DEF" -> 5.0;
                case "ATT" -> 8.0;
                case "WINGER" -> 12.0;
                default -> 12.0;
            }, 8.0, 92.0);
            case RIGHT_OVERLOAD -> x = clamp(x + switch (position) {
                case "DEF" -> 5.0;
                case "ATT" -> 8.0;
                case "WINGER" -> 12.0;
                default -> 12.0;
            }, 8.0, 92.0);
        }
        return new double[] {clamp(x, 0.0, 100.0), clamp(y, 0.0, 100.0)};
    }

    private double pick(int index, double... values) {
        if (values.length == 0) return 50.0;
        return values[Math.floorMod(index, values.length)];
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String fallbackSubdivision(String position) {
        String normalized = position != null ? position.toUpperCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "GK" -> "GK-1";
            case "RB", "RWB" -> "S24-3";
            case "LB", "LWB" -> "S22-1";
            case "CB", "DEF" -> "S23-2";
            case "DM", "CDM" -> "S20-2";
            case "LM" -> "S16-1";
            case "RM" -> "S18-3";
            case "CM", "MID", "AM", "CAM" -> "S17-2";
            case "LW" -> "S04-1";
            case "RW" -> "S06-3";
            case "ST", "CF", "ATT", "WINGER" -> "S05-2";
            default -> "S17-2";
        };
    }

    private Optional<SubPlan> chooseImpactSubstitution(
            CareerSave career,
            MatchFixture fixture,
            String userTeamId,
            SessionTeam home,
            SessionTeam away) {

        boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
        V24MatchContext context = v24ContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            userIsHome ? TeamStyle.BALANCED : home.getStyle(),
            userIsHome ? away.getStyle() : TeamStyle.BALANCED,
            12345L);

        List<SessionPlayer> starters = userIsHome ? context.homeStartingPlayers() : context.awayStartingPlayers();
        List<SessionPlayer> bench = userIsHome ? context.homeBenchPlayers() : context.awayBenchPlayers();

        return starters.stream()
            .filter(this::isOutfieldPlayer)
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> impactSubPositionPriority(p.getPosition()))
                .thenComparingInt(this::substitutionScore)
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .map(off -> bestBenchReplacement(off, bench)
                .map(on -> new SubPlan(
                    off.getSessionPlayerId(),
                    on.getSessionPlayerId(),
                    safeName(off),
                    safeName(on),
                    off.getPosition(),
                    on.getPosition(),
                    substitutionScore(on) - substitutionScore(off))))
            .flatMap(Optional::stream)
            .filter(plan -> plan.scoreDelta() >= 25)
            .findFirst();
    }

    private Optional<SessionPlayer> bestBenchReplacement(SessionPlayer off, List<SessionPlayer> bench) {
        Optional<SessionPlayer> exactRole = bench.stream()
            .filter(this::isOutfieldPlayer)
            .filter(p -> p.getSessionPlayerId() != null && !p.getSessionPlayerId().isBlank())
            .filter(p -> Objects.equals(off.getPosition(), p.getPosition()))
            .max(Comparator
                .comparingInt(this::substitutionScore)
                .thenComparingInt(p -> safeInt(p.getTechnique()))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)));
        if (exactRole.isPresent() && substitutionScore(exactRole.get()) > substitutionScore(off)) {
            return exactRole;
        }
        return bench.stream()
            .filter(this::isOutfieldPlayer)
            .filter(p -> p.getSessionPlayerId() != null && !p.getSessionPlayerId().isBlank())
            .filter(p -> samePosition(off, p))
            .filter(p -> substitutionScore(p) > substitutionScore(off))
            .max(Comparator
                .comparingInt(this::substitutionScore)
                .thenComparingInt(p -> safeInt(p.getTechnique()))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)));
    }

    private Optional<SubPlan> chooseScoredSubstitution(
            CareerSave career,
            MatchFixture fixture,
            String userTeamId,
            SessionTeam home,
            SessionTeam away,
            boolean upgrade) {
        return chooseScoredSubstitution(career, fixture, userTeamId, home, away, upgrade, Set.of());
    }

    private Optional<SubPlan> chooseScoredSubstitution(
            CareerSave career,
            MatchFixture fixture,
            String userTeamId,
            SessionTeam home,
            SessionTeam away,
            boolean upgrade,
            Set<String> allowedPositions) {

        boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
        V24MatchContext context = v24ContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            userIsHome ? TeamStyle.BALANCED : home.getStyle(),
            userIsHome ? away.getStyle() : TeamStyle.BALANCED,
            12345L);

        List<SessionPlayer> starters = userIsHome ? context.homeStartingPlayers() : context.awayStartingPlayers();
        List<SessionPlayer> bench = userIsHome ? context.homeBenchPlayers() : context.awayBenchPlayers();

        return starters.stream()
            .filter(this::isOutfieldPlayer)
            .filter(off -> allowedPositions == null
                || allowedPositions.isEmpty()
                || allowedPositions.contains(off.getPosition()))
            .flatMap(off -> bench.stream()
                .filter(this::isOutfieldPlayer)
                .filter(on -> upgrade
                    ? Objects.equals(off.getPosition(), on.getPosition())
                    : samePosition(off, on))
                .map(on -> toSubPlan(off, on)))
            .filter(plan -> upgrade ? plan.scoreDelta() > 0 : plan.scoreDelta() < 0)
            .max(upgrade
                ? Comparator.comparingInt(SubPlan::scoreDelta)
                : Comparator.comparingInt((SubPlan plan) -> -plan.scoreDelta()));
    }

    private boolean samePosition(SessionPlayer off, SessionPlayer on) {
        return off != null
            && on != null
            && off.getPosition() != null
            && (off.getPosition().equals(on.getPosition())
                || Objects.equals(positionPixelAutoLine(off), positionPixelAutoLine(on)));
    }

    private SubPlan toSubPlan(SessionPlayer off, SessionPlayer on) {
        return new SubPlan(
            off.getSessionPlayerId(),
            on.getSessionPlayerId(),
            safeName(off),
            safeName(on),
            off.getPosition(),
            on.getPosition(),
            substitutionScore(on) - substitutionScore(off));
    }

    private int impactSubPositionPriority(String position) {
        return switch (positionPixelAutoLine(position)) {
            case "ATT" -> 0;
            case "MID" -> 1;
            case "DEF" -> 2;
            default -> 3;
        };
    }

    private boolean isOutfieldPlayer(SessionPlayer player) {
        return player != null
            && player.getSessionPlayerId() != null
            && !player.getSessionPlayerId().isBlank()
            && player.getPosition() != null
            && !"GK".equals(player.getPosition());
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private int substitutionScore(SessionPlayer player) {
        if (player == null) {
            return 0;
        }
        String normalizedLine = positionPixelAutoLine(player);
        if ("WINGER".equalsIgnoreCase(player.getPosition())
            || "LW".equalsIgnoreCase(player.getPosition())
            || "RW".equalsIgnoreCase(player.getPosition())) {
            return safeInt(player.getAttack()) * 2
                + safeInt(player.getSpeed()) * 2
                + safeInt(player.getTechnique())
                + safeInt(player.getMentality());
        }
        return switch (normalizedLine) {
            case "DEF" -> safeInt(player.getDefense()) * 3
                + safeInt(player.getMentality()) * 2
                + safeInt(player.getSpeed())
                + safeInt(player.getStamina());
            case "MID" -> safeInt(player.getTechnique()) * 2
                + safeInt(player.getMentality()) * 2
                + safeInt(player.getAttack())
                + safeInt(player.getDefense())
                + safeInt(player.getStamina());
            case "WINGER" -> safeInt(player.getAttack()) * 2
                + safeInt(player.getSpeed()) * 2
                + safeInt(player.getTechnique())
                + safeInt(player.getMentality());
            case "ATT" -> safeInt(player.getAttack()) * 3
                + safeInt(player.getTechnique()) * 2
                + safeInt(player.getSpeed())
                + safeInt(player.getMentality());
            default -> safeInt(player.getAttack())
                + safeInt(player.getDefense())
                + safeInt(player.getTechnique())
                + safeInt(player.getSpeed())
                + safeInt(player.getStamina())
                + safeInt(player.getMentality());
        };
    }

    private Optional<SessionPlayer> findPlayer(List<SessionPlayer> players, String playerId) {
        if (players == null || playerId == null || playerId.isBlank()) {
            return Optional.empty();
        }
        return players.stream()
            .filter(p -> p != null && playerId.equals(p.getSessionPlayerId()))
            .findFirst();
    }

    private boolean isAutoPlayerSwapToken(String playerId) {
        return AUTO_PLAYER_SWAP_STARTER.equals(playerId)
            || AUTO_PLAYER_SWAP_BENCH.equals(playerId)
            || (playerId != null && playerId.startsWith(AUTO_PLAYER_SWAP_PREFIX));
    }

    private String autoPlayerSwapMode(String starterPlayerId, String benchPlayerId) {
        String token = starterPlayerId != null && starterPlayerId.startsWith(AUTO_PLAYER_SWAP_PREFIX)
            ? starterPlayerId
            : benchPlayerId;
        if (token == null || !token.startsWith(AUTO_PLAYER_SWAP_PREFIX)) {
            return "NATURAL";
        }
        return token.substring(AUTO_PLAYER_SWAP_PREFIX.length()).toUpperCase(Locale.ROOT);
    }

    private Optional<PlayerSwapAutoPair> chooseAutoPlayerSwapPair(
            List<SessionPlayer> starters,
            List<SessionPlayer> bench,
            String mode) {
        if (starters == null || bench == null || starters.isEmpty() || bench.isEmpty()) {
            return Optional.empty();
        }
        List<SessionPlayer> outfieldStarters = starters.stream()
            .filter(this::isOutfieldPlayer)
            .filter(p -> p.getSessionPlayerId() != null && !p.getSessionPlayerId().isBlank())
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> impactSubPositionPriority(p.getPosition()))
                .thenComparingInt(this::substitutionScore)
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .toList();
        List<SessionPlayer> outfieldBench = bench.stream()
            .filter(this::isOutfieldPlayer)
            .filter(p -> p.getSessionPlayerId() != null && !p.getSessionPlayerId().isBlank())
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> -substitutionScore(p))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .toList();

        Optional<PlayerSwapAutoPair> stressPair = switch (String.valueOf(mode).toUpperCase(Locale.ROOT)) {
            case "ATT_TO_DEF" -> chooseAutoSwapByLines(outfieldStarters, outfieldBench, "ATT", "DEF");
            case "DEF_TO_ATT" -> chooseAutoSwapByLines(outfieldStarters, outfieldBench, "DEF", "ATT");
            case "MID_TO_ATT" -> chooseAutoSwapByLines(outfieldStarters, outfieldBench, "MID", "ATT");
            case "MID_TO_DEF" -> chooseAutoSwapByLines(outfieldStarters, outfieldBench, "MID", "DEF");
            case "DOWNGRADE" -> chooseAutoSwapByOverallGap(outfieldStarters, outfieldBench, false);
            case "UPGRADE" -> chooseAutoSwapByOverallGap(outfieldStarters, outfieldBench, true);
            case "OUT_OF_LINE" -> chooseAutoSwapOutOfLine(outfieldStarters, outfieldBench);
            default -> Optional.empty();
        };
        if (stressPair.isPresent()) {
            return stressPair;
        }

        for (SessionPlayer starter : outfieldStarters) {
            Optional<SessionPlayer> samePosition = outfieldBench.stream()
                .filter(candidate -> samePosition(starter, candidate))
                .findFirst();
            if (samePosition.isPresent()) {
                return Optional.of(new PlayerSwapAutoPair(starter, samePosition.get()));
            }

            Optional<SessionPlayer> sameLine = outfieldBench.stream()
                .filter(candidate -> Objects.equals(
                    positionPixelAutoLine(starter),
                    positionPixelAutoLine(candidate)))
                .findFirst();
            if (sameLine.isPresent()) {
                return Optional.of(new PlayerSwapAutoPair(starter, sameLine.get()));
            }
        }

        if (!outfieldStarters.isEmpty() && !outfieldBench.isEmpty()) {
            return Optional.of(new PlayerSwapAutoPair(outfieldStarters.get(0), outfieldBench.get(0)));
        }
        return Optional.empty();
    }

    private Optional<PlayerSwapAutoPair> chooseAutoSwapByLines(
            List<SessionPlayer> starters,
            List<SessionPlayer> bench,
            String starterLine,
            String benchLine) {
        return starters.stream()
            .filter(starter -> starterLine.equals(positionPixelAutoLine(starter)))
            .flatMap(starter -> bench.stream()
                .filter(candidate -> benchLine.equals(positionPixelAutoLine(candidate)))
                .map(candidate -> new PlayerSwapAutoPair(starter, candidate)))
            .findFirst();
    }

    private Optional<PlayerSwapAutoPair> chooseAutoSwapOutOfLine(
            List<SessionPlayer> starters,
            List<SessionPlayer> bench) {
        return starters.stream()
            .flatMap(starter -> bench.stream()
                .filter(candidate -> !Objects.equals(positionPixelAutoLine(starter), positionPixelAutoLine(candidate)))
                .map(candidate -> new PlayerSwapAutoPair(starter, candidate)))
            .findFirst();
    }

    private Optional<PlayerSwapAutoPair> chooseAutoSwapByOverallGap(
            List<SessionPlayer> starters,
            List<SessionPlayer> bench,
            boolean upgrade) {
        return starters.stream()
            .flatMap(starter -> bench.stream()
                .filter(candidate -> {
                    int delta = playerOverall(candidate) - playerOverall(starter);
                    return upgrade ? delta >= 4 : delta <= -4;
                })
                .sorted((a, b) -> {
                    int deltaA = playerOverall(a) - playerOverall(starter);
                    int deltaB = playerOverall(b) - playerOverall(starter);
                    return upgrade ? Integer.compare(deltaB, deltaA) : Integer.compare(deltaA, deltaB);
                })
                .map(candidate -> new PlayerSwapAutoPair(starter, candidate)))
            .findFirst();
    }

    private Optional<SessionPlayer> resolvePositionPixelPlayer(List<SessionPlayer> players, String playerId) {
        if (players == null || playerId == null || playerId.isBlank()) {
            return Optional.empty();
        }
        if (!playerId.startsWith(AUTO_POSITION_PIXEL_PREFIX)) {
            return findPlayer(players, playerId);
        }

        String requestedLine = playerId.substring(AUTO_POSITION_PIXEL_PREFIX.length()).toUpperCase(Locale.ROOT);
        Optional<SessionPlayer> exactLine = players.stream()
            .filter(p -> p != null && requestedLine.equals(positionPixelAutoLine(p)))
            .findFirst();
        if (exactLine.isPresent()) {
            return exactLine;
        }
        return players.stream()
            .filter(p -> p != null && !"GK".equalsIgnoreCase(p.getPosition()))
            .findFirst();
    }

    private String positionPixelAutoLine(SessionPlayer player) {
        if (player == null || player.getPosition() == null) {
            return "MID";
        }
        return positionPixelAutoLine(player.getPosition());
    }

    private String positionPixelAutoLine(String rawPosition) {
        if (rawPosition == null || rawPosition.isBlank()) {
            return "MID";
        }
        String position = rawPosition.toUpperCase(Locale.ROOT);
        return switch (position) {
            case "GK" -> "GK";
            case "DEF", "CB", "LB", "RB", "LWB", "RWB" -> "DEF";
            case "ATT", "ST", "CF", "LW", "RW", "WINGER" -> "ATT";
            default -> "MID";
        };
    }

    private V24MatchContext buildInitialSwapContext(
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

        SessionPlayer starter = findPlayer(userStarters, starterPlayerId)
            .orElseThrow(() -> new IllegalArgumentException(
                "starterPlayerId '" + starterPlayerId + "' not in user starting XI"));
        SessionPlayer bench = findPlayer(userBench, benchPlayerId)
            .orElseThrow(() -> new IllegalArgumentException(
                "benchPlayerId '" + benchPlayerId + "' not on user bench"));

        for (int i = 0; i < userStarters.size(); i++) {
            SessionPlayer current = userStarters.get(i);
            if (current != null && starterPlayerId.equals(current.getSessionPlayerId())) {
                userStarters.set(i, bench);
                break;
            }
        }
        for (int i = 0; i < userBench.size(); i++) {
            SessionPlayer current = userBench.get(i);
            if (current != null && benchPlayerId.equals(current.getSessionPlayerId())) {
                userBench.set(i, starter);
                break;
            }
        }

        Map<String, LineupSlotDTO> homeSlots = context.homeSlotsByPlayerId();
        Map<String, LineupSlotDTO> awaySlots = context.awaySlotsByPlayerId();
        Map<String, LineupSlotDTO> userSlots = new LinkedHashMap<>(
            userIsHome ? homeSlots : awaySlots);
        LineupSlotDTO previousSlot = userSlots.remove(starterPlayerId);
        if (previousSlot != null) {
            userSlots.put(benchPlayerId, new LineupSlotDTO(
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

    private V24MatchContext buildMovedPositionContext(
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
        Map<String, LineupSlotDTO> homeSlots = context.homeSlotsByPlayerId();
        Map<String, LineupSlotDTO> awaySlots = context.awaySlotsByPlayerId();
        Map<String, LineupSlotDTO> userSlots = new LinkedHashMap<>(userIsHome ? homeSlots : awaySlots);
        LineupSlotDTO previous = userSlots.get(playerId);
        userSlots.put(playerId, new LineupSlotDTO(
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

    private V24MatchContext buildRoleOverrideContext(
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

    private SessionPlayer roleOverrideClone(SessionPlayer source, String naturalPosition) {
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

    private double clampPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double fallbackYPercent(String position) {
        String normalized = position != null ? position.toUpperCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "GK" -> 94.0;
            case "RB", "RWB", "LB", "LWB", "CB", "DEF" -> 78.0;
            case "DM", "CDM" -> 66.0;
            case "CM", "MID", "LM", "RM" -> 52.0;
            case "AM", "CAM" -> 38.0;
            case "ST", "CF", "LW", "RW", "ATT", "WINGER" -> 18.0;
            default -> 52.0;
        };
    }

    private Optional<Double> canonicalXPercent(String subdivisionId) {
        int[] parsed = parseSubdivision(subdivisionId);
        if (parsed == null) {
            return Optional.empty();
        }
        int sector = parsed[0];
        int subIndex = parsed[1];
        int sectorCol = (sector - 1) % 3;
        double left = (sectorCol * 3 + (subIndex - 1)) * 11.11;
        return Optional.of(clampPercent(left + 11.11 / 2.0));
    }

    private Optional<Double> canonicalYPercent(String subdivisionId) {
        if ("GK-1".equals(subdivisionId)) {
            return Optional.of(93.0);
        }
        int[] parsed = parseSubdivision(subdivisionId);
        if (parsed == null) {
            return Optional.empty();
        }
        int sector = parsed[0];
        int sectorRow = (sector - 1) / 3;
        double top = sectorRow * 11.11;
        return Optional.of(clampPercent(top + 11.11 / 2.0));
    }

    private int[] parseSubdivision(String subdivisionId) {
        if (subdivisionId == null || !subdivisionId.startsWith("S")) {
            return null;
        }
        int dash = subdivisionId.indexOf('-');
        if (dash < 0 || dash >= subdivisionId.length() - 1) {
            return null;
        }
        try {
            int sector = Integer.parseInt(subdivisionId.substring(1, dash));
            int subIndex = Integer.parseInt(subdivisionId.substring(dash + 1));
            if (sector < 1 || sector > 27 || subIndex < 1 || subIndex > 3) {
                return null;
            }
            return new int[] { sector, subIndex };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String resolveSlotId(
            V24MatchContext context,
            boolean userIsHome,
            String starterPlayerId,
            String requestedSlotId) {
        Map<String, LineupSlotDTO> slots = userIsHome
            ? context.homeSlotsByPlayerId()
            : context.awaySlotsByPlayerId();
        LineupSlotDTO slot = slots.get(starterPlayerId);
        if (slot != null && slot.subdivisionId() != null && !slot.subdivisionId().isBlank()) {
            return slot.subdivisionId();
        }
        return requestedSlotId;
    }

    private Integer playerOverall(SessionPlayer player) {
        if (player == null) {
            return null;
        }
        return Math.round((safeInt(player.getAttack())
            + safeInt(player.getDefense())
            + safeInt(player.getTechnique())
            + safeInt(player.getSpeed())
            + safeInt(player.getStamina())
            + safeInt(player.getMentality())) / 6.0f);
    }

    private String safeName(SessionPlayer player) {
        return player != null && player.getName() != null && !player.getName().isBlank()
            ? player.getName()
            : "Unknown";
    }

    private String currentFormation(CareerSave career, String teamId, SessionTeam team) {
        Map<String, String> formations = career.getTeamStarting11Formation();
        if (formations != null && formations.get(teamId) != null && !formations.get(teamId).isBlank()) {
            return formations.get(teamId);
        }
        return team.getFormation();
    }

    private ZoneCounts countZones(V24DetailedMatchResult result) {
        int homeCentral = 0, homeWide = 0, homeLong = 0;
        int awayCentral = 0, awayWide = 0, awayLong = 0;
        int homeLeftWide = 0, homeRightWide = 0;
        int awayLeftWide = 0, awayRightWide = 0;
        double homeCentralXg = 0.0, homeWideXg = 0.0, homeLongXg = 0.0;
        double awayCentralXg = 0.0, awayWideXg = 0.0, awayLongXg = 0.0;
        double homeLeftWideXg = 0.0, homeRightWideXg = 0.0;
        double awayLeftWideXg = 0.0, awayRightWideXg = 0.0;
        for (V24MatchEvent event : result.timeline().events()) {
            if (!isShotLike(event) || event.shotCoordinate() == null) {
                continue;
            }
            V24ShotLocation location = event.shotCoordinate().location();
            boolean home = result.homeTeamId().equals(event.teamId());
            if (location == V24ShotLocation.SIX_YARD_BOX || location == V24ShotLocation.PENALTY_AREA_CENTER) {
                if (home) {
                    homeCentral++;
                    homeCentralXg += event.xg();
                } else {
                    awayCentral++;
                    awayCentralXg += event.xg();
                }
            } else if (location == V24ShotLocation.PENALTY_AREA_WIDE) {
                if (home) {
                    homeWide++;
                    homeWideXg += event.xg();
                    if (isLeftWide(event)) {
                        homeLeftWide++;
                        homeLeftWideXg += event.xg();
                    } else {
                        homeRightWide++;
                        homeRightWideXg += event.xg();
                    }
                } else {
                    awayWide++;
                    awayWideXg += event.xg();
                    if (isLeftWide(event)) {
                        awayLeftWide++;
                        awayLeftWideXg += event.xg();
                    } else {
                        awayRightWide++;
                        awayRightWideXg += event.xg();
                    }
                }
            } else {
                if (home) {
                    homeLong++;
                    homeLongXg += event.xg();
                } else {
                    awayLong++;
                    awayLongXg += event.xg();
                }
            }
        }
        return new ZoneCounts(
            homeCentral,
            homeWide,
            homeLong,
            awayCentral,
            awayWide,
            awayLong,
            round3(homeCentralXg),
            round3(homeWideXg),
            round3(homeLongXg),
            homeLeftWide,
            homeRightWide,
            round3(homeLeftWideXg),
            round3(homeRightWideXg),
            round3(awayCentralXg),
            round3(awayWideXg),
            round3(awayLongXg),
            awayLeftWide,
            awayRightWide,
            round3(awayLeftWideXg),
            round3(awayRightWideXg));
    }

    private boolean isLeftWide(V24MatchEvent event) {
        return event.shotCoordinate() != null && event.shotCoordinate().y() < 50.0;
    }

    private boolean isShotLike(V24MatchEvent event) {
        return event.type() == V24MatchEventType.SHOT
            || event.type() == V24MatchEventType.SHOT_ON_TARGET
            || event.type() == V24MatchEventType.MISS
            || event.type() == V24MatchEventType.BLOCK
            || event.type() == V24MatchEventType.GOAL;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class FormationSummaryAccumulator {
        private final String formation;
        private int count;
        private double goalsFor;
        private double goalsAgainst;
        private double possessionFor;
        private double shotsFor;
        private double shotsAgainst;
        private double xgFor;
        private double xgAgainst;
        private double centralShotsFor;
        private double wideShotsFor;
        private double longShotsFor;
        private double centralShotsAgainst;
        private double wideShotsAgainst;
        private double longShotsAgainst;
        private double leftWideShotsFor;
        private double rightWideShotsFor;
        private double leftWideShotsAgainst;
        private double rightWideShotsAgainst;
        private double leftWideXgFor;
        private double rightWideXgFor;
        private double leftWideXgAgainst;
        private double rightWideXgAgainst;
        private double shapePossessionMultiplier;
        private double shapeAttackVolumeMultiplier;
        private double shapeDefensiveResistanceMultiplier;
        private double shapeAttackLeft;
        private double shapeAttackCenter;
        private double shapeAttackRight;
        private double shapeDefenseLeft;
        private double shapeDefenseCenter;
        private double shapeDefenseRight;

        private FormationSummaryAccumulator(String formation) {
            this.formation = formation;
        }

        private void add(FormationMatrixRow row, boolean userIsHome) {
            count++;
            goalsFor += userIsHome ? row.homeGoals() : row.awayGoals();
            goalsAgainst += userIsHome ? row.awayGoals() : row.homeGoals();
            possessionFor += userIsHome ? row.homePossession() : row.awayPossession();
            shotsFor += userIsHome ? row.homeShots() : row.awayShots();
            shotsAgainst += userIsHome ? row.awayShots() : row.homeShots();
            xgFor += userIsHome ? row.homeXg() : row.awayXg();
            xgAgainst += userIsHome ? row.awayXg() : row.homeXg();
            centralShotsFor += userIsHome ? row.homeCentralShots() : row.awayCentralShots();
            wideShotsFor += userIsHome ? row.homeWideShots() : row.awayWideShots();
            longShotsFor += userIsHome ? row.homeLongShots() : row.awayLongShots();
            centralShotsAgainst += userIsHome ? row.awayCentralShots() : row.homeCentralShots();
            wideShotsAgainst += userIsHome ? row.awayWideShots() : row.homeWideShots();
            longShotsAgainst += userIsHome ? row.awayLongShots() : row.homeLongShots();
            leftWideShotsFor += userIsHome ? row.homeLeftWideShots() : row.awayLeftWideShots();
            rightWideShotsFor += userIsHome ? row.homeRightWideShots() : row.awayRightWideShots();
            leftWideShotsAgainst += userIsHome ? row.awayLeftWideShots() : row.homeLeftWideShots();
            rightWideShotsAgainst += userIsHome ? row.awayRightWideShots() : row.homeRightWideShots();
            leftWideXgFor += userIsHome ? row.homeLeftWideXg() : row.awayLeftWideXg();
            rightWideXgFor += userIsHome ? row.homeRightWideXg() : row.awayRightWideXg();
            leftWideXgAgainst += userIsHome ? row.awayLeftWideXg() : row.homeLeftWideXg();
            rightWideXgAgainst += userIsHome ? row.awayRightWideXg() : row.homeRightWideXg();
            shapePossessionMultiplier += row.shapePossessionMultiplier();
            shapeAttackVolumeMultiplier += row.shapeAttackVolumeMultiplier();
            shapeDefensiveResistanceMultiplier += row.shapeDefensiveResistanceMultiplier();
            shapeAttackLeft += row.shapeAttackLeft();
            shapeAttackCenter += row.shapeAttackCenter();
            shapeAttackRight += row.shapeAttackRight();
            shapeDefenseLeft += row.shapeDefenseLeft();
            shapeDefenseCenter += row.shapeDefenseCenter();
            shapeDefenseRight += row.shapeDefenseRight();
        }

        private FormationMatrixSummaryRow toRow(long seedStart, int seedCount) {
            int safeCount = Math.max(1, count);
            double avgGoalsFor = round2(goalsFor / safeCount);
            double avgGoalsAgainst = round2(goalsAgainst / safeCount);
            double avgShotsFor = round2(shotsFor / safeCount);
            double avgShotsAgainst = round2(shotsAgainst / safeCount);
            double avgXgFor = round3(xgFor / safeCount);
            double avgXgAgainst = round3(xgAgainst / safeCount);
            return new FormationMatrixSummaryRow(
                formation,
                seedStart,
                seedStart + seedCount - 1L,
                seedCount,
                avgGoalsFor,
                avgGoalsAgainst,
                round2(avgGoalsFor - avgGoalsAgainst),
                round2(possessionFor / safeCount),
                avgShotsFor,
                avgShotsAgainst,
                round2(avgShotsFor - avgShotsAgainst),
                avgXgFor,
                avgXgAgainst,
                round3(avgXgFor - avgXgAgainst),
                round2(centralShotsFor / safeCount),
                round2(wideShotsFor / safeCount),
                round2(longShotsFor / safeCount),
                round2(centralShotsAgainst / safeCount),
                round2(wideShotsAgainst / safeCount),
                round2(longShotsAgainst / safeCount),
                round2(leftWideShotsFor / safeCount),
                round2(rightWideShotsFor / safeCount),
                round2(leftWideShotsAgainst / safeCount),
                round2(rightWideShotsAgainst / safeCount),
                round3(leftWideXgFor / safeCount),
                round3(rightWideXgFor / safeCount),
                round3(leftWideXgAgainst / safeCount),
                round3(rightWideXgAgainst / safeCount),
                round3(shapePossessionMultiplier / safeCount),
                round3(shapeAttackVolumeMultiplier / safeCount),
                round3(shapeDefensiveResistanceMultiplier / safeCount),
                round3(shapeAttackLeft / safeCount),
                round3(shapeAttackCenter / safeCount),
                round3(shapeAttackRight / safeCount),
                round3(shapeDefenseLeft / safeCount),
                round3(shapeDefenseCenter / safeCount),
                round3(shapeDefenseRight / safeCount));
        }
    }

    private final class SwapAccumulator {
        private int count;
        private double goalsFor;
        private double goalsAgainst;
        private double shotsFor;
        private double shotsAgainst;
        private double possessionFor;
        private double xgFor;
        private double xgAgainst;
        private double centralShotsFor;
        private double wideShotsFor;
        private double longShotsFor;
        private double centralShotsAgainst;
        private double wideShotsAgainst;
        private double longShotsAgainst;
        private double centralXgFor;
        private double wideXgFor;
        private double longXgFor;
        private double centralXgAgainst;
        private double wideXgAgainst;
        private double longXgAgainst;
        private double leftWideShotsFor;
        private double rightWideShotsFor;
        private double leftWideShotsAgainst;
        private double rightWideShotsAgainst;
        private double leftWideXgFor;
        private double rightWideXgFor;
        private double leftWideXgAgainst;
        private double rightWideXgAgainst;

        private void add(V24DetailedMatchResult result, boolean userIsHome) {
            ZoneCounts zones = countZones(result);
            count++;
            goalsFor += userIsHome ? result.homeGoals() : result.awayGoals();
            goalsAgainst += userIsHome ? result.awayGoals() : result.homeGoals();
            shotsFor += userIsHome ? result.homeShots() : result.awayShots();
            shotsAgainst += userIsHome ? result.awayShots() : result.homeShots();
            possessionFor += userIsHome ? result.homePossession() : result.awayPossession();
            xgFor += userIsHome ? result.homeXg() : result.awayXg();
            xgAgainst += userIsHome ? result.awayXg() : result.homeXg();
            centralShotsFor += userIsHome ? zones.homeCentral() : zones.awayCentral();
            wideShotsFor += userIsHome ? zones.homeWide() : zones.awayWide();
            longShotsFor += userIsHome ? zones.homeLong() : zones.awayLong();
            centralShotsAgainst += userIsHome ? zones.awayCentral() : zones.homeCentral();
            wideShotsAgainst += userIsHome ? zones.awayWide() : zones.homeWide();
            longShotsAgainst += userIsHome ? zones.awayLong() : zones.homeLong();
            centralXgFor += userIsHome ? zones.homeCentralXg() : zones.awayCentralXg();
            wideXgFor += userIsHome ? zones.homeWideXg() : zones.awayWideXg();
            longXgFor += userIsHome ? zones.homeLongXg() : zones.awayLongXg();
            centralXgAgainst += userIsHome ? zones.awayCentralXg() : zones.homeCentralXg();
            wideXgAgainst += userIsHome ? zones.awayWideXg() : zones.homeWideXg();
            longXgAgainst += userIsHome ? zones.awayLongXg() : zones.homeLongXg();
            leftWideShotsFor += userIsHome ? zones.homeLeftWide() : zones.awayLeftWide();
            rightWideShotsFor += userIsHome ? zones.homeRightWide() : zones.awayRightWide();
            leftWideShotsAgainst += userIsHome ? zones.awayLeftWide() : zones.homeLeftWide();
            rightWideShotsAgainst += userIsHome ? zones.awayRightWide() : zones.homeRightWide();
            leftWideXgFor += userIsHome ? zones.homeLeftWideXg() : zones.awayLeftWideXg();
            rightWideXgFor += userIsHome ? zones.homeRightWideXg() : zones.awayRightWideXg();
            leftWideXgAgainst += userIsHome ? zones.awayLeftWideXg() : zones.homeLeftWideXg();
            rightWideXgAgainst += userIsHome ? zones.awayRightWideXg() : zones.homeRightWideXg();
        }

        private SwapAverages averages() {
            if (count <= 0) {
                return new SwapAverages(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
            double avgGoalsFor = round2(goalsFor / count);
            double avgGoalsAgainst = round2(goalsAgainst / count);
            double avgShotsFor = round2(shotsFor / count);
            double avgShotsAgainst = round2(shotsAgainst / count);
            double avgXgFor = round3(xgFor / count);
            double avgXgAgainst = round3(xgAgainst / count);
            return new SwapAverages(
                avgGoalsFor,
                avgGoalsAgainst,
                round2(avgGoalsFor - avgGoalsAgainst),
                avgShotsFor,
                avgShotsAgainst,
                round2(possessionFor / count),
                avgXgFor,
                avgXgAgainst,
                round3(avgXgFor - avgXgAgainst),
                round2(centralShotsFor / count),
                round2(wideShotsFor / count),
                round2(longShotsFor / count),
                round2(centralShotsAgainst / count),
                round2(wideShotsAgainst / count),
                round2(longShotsAgainst / count),
                round3(centralXgFor / count),
                round3(wideXgFor / count),
                round3(longXgFor / count),
                round3(centralXgAgainst / count),
                round3(wideXgAgainst / count),
                round3(longXgAgainst / count),
                round2(leftWideShotsFor / count),
                round2(rightWideShotsFor / count),
                round2(leftWideShotsAgainst / count),
                round2(rightWideShotsAgainst / count),
                round3(leftWideXgFor / count),
                round3(rightWideXgFor / count),
                round3(leftWideXgAgainst / count),
                round3(rightWideXgAgainst / count));
        }
    }

    private record SwapAverages(
        double goalsFor,
        double goalsAgainst,
        double goalDiff,
        double shotsFor,
        double shotsAgainst,
        double possessionFor,
        double xgFor,
        double xgAgainst,
        double xgDiff,
        double centralShotsFor,
        double wideShotsFor,
        double longShotsFor,
        double centralShotsAgainst,
        double wideShotsAgainst,
        double longShotsAgainst,
        double centralXgFor,
        double wideXgFor,
        double longXgFor,
        double centralXgAgainst,
        double wideXgAgainst,
        double longXgAgainst,
        double leftWideShotsFor,
        double rightWideShotsFor,
        double leftWideShotsAgainst,
        double rightWideShotsAgainst,
        double leftWideXgFor,
        double rightWideXgFor,
        double leftWideXgAgainst,
        double rightWideXgAgainst
    ) {}

    private record ZoneCounts(
        int homeCentral,
        int homeWide,
        int homeLong,
        int awayCentral,
        int awayWide,
        int awayLong,
        double homeCentralXg,
        double homeWideXg,
        double homeLongXg,
        int homeLeftWide,
        int homeRightWide,
        double homeLeftWideXg,
        double homeRightWideXg,
        double awayCentralXg,
        double awayWideXg,
        double awayLongXg,
        int awayLeftWide,
        int awayRightWide,
        double awayLeftWideXg,
        double awayRightWideXg
    ) {}

    private enum ScenarioActionType {
        NONE,
        NOOP_REPLAY,
        STYLE,
        OPPONENT_STYLE,
        FORMATION,
        POSITION,
        SUBSTITUTION,
        POSITION_AND_SUBSTITUTION
    }

    private enum ShapePreset {
        COMPACT_CENTER,
        WIDE_OVERLOAD,
        ATTACKING_STEP,
        ATTACKING_HIGH,
        HIGH_PRESS,
        DOUBLE_STRIKER,
        ALL_OUT,
        DEFENSIVE_STEP,
        DEFENSIVE_LOW,
        LEFT_OVERLOAD,
        RIGHT_OVERLOAD
    }

    private record ScenarioAction(
        ScenarioActionType type,
        TeamStyle changedStyle,
        String changedFormation,
        Map<String, LineupSlotDTO> formationSlotsByPlayerId,
        PositionPlan positionPlan,
        SubPlan subPlan
    ) {
        static ScenarioAction none() {
            return new ScenarioAction(ScenarioActionType.NONE, null, null, null, null, null);
        }

        static ScenarioAction noopReplay() {
            return new ScenarioAction(ScenarioActionType.NOOP_REPLAY, null, null, null, null, null);
        }

        static ScenarioAction style(TeamStyle style) {
            return new ScenarioAction(ScenarioActionType.STYLE, style, null, null, null, null);
        }

        static ScenarioAction opponentStyle(TeamStyle style) {
            return new ScenarioAction(ScenarioActionType.OPPONENT_STYLE, style, null, null, null, null);
        }

        static ScenarioAction formation(String formation) {
            return formation(formation, null);
        }

        static ScenarioAction formation(String formation, Map<String, LineupSlotDTO> slotsByPlayerId) {
            return new ScenarioAction(ScenarioActionType.FORMATION, null, formation, slotsByPlayerId, null, null);
        }

        static ScenarioAction position(PositionPlan positionPlan) {
            return new ScenarioAction(ScenarioActionType.POSITION, null, null, null, positionPlan, null);
        }

        static ScenarioAction substitution(SubPlan subPlan) {
            return new ScenarioAction(ScenarioActionType.SUBSTITUTION, null, null, null, null, subPlan);
        }

        static ScenarioAction positionAndSubstitution(PositionPlan positionPlan, SubPlan subPlan) {
            return new ScenarioAction(
                ScenarioActionType.POSITION_AND_SUBSTITUTION,
                null,
                null,
                null,
                positionPlan,
                subPlan);
        }

        String detail() {
            return switch (type) {
                case NONE -> "Sin cambios";
                case NOOP_REPLAY -> "Replay sin cambio";
                case STYLE -> changedStyle != null ? changedStyle.name() : "Style change";
                case OPPONENT_STYLE -> changedStyle != null
                    ? "Opponent " + changedStyle.name()
                    : "Opponent style change";
                case FORMATION -> changedFormation != null ? changedFormation : "Formation change";
                case POSITION -> positionPlan != null
                    ? positionPlan.playerName() + " -> x"
                        + Math.round(positionPlan.xPercent()) + "/y"
                        + Math.round(positionPlan.yPercent())
                    : "Position change";
                case SUBSTITUTION -> subPlan != null
                    ? subPlan.offName() + " (" + subPlan.offPosition() + ") -> "
                        + subPlan.onName() + " (" + subPlan.onPosition() + ")"
                        + " [" + (subPlan.scoreDelta() >= 0 ? "+" : "") + subPlan.scoreDelta() + "]"
                    : "Substitution";
                case POSITION_AND_SUBSTITUTION -> {
                    String shape = positionPlan != null ? positionPlan.playerName() : "shape";
                    String sub = subPlan != null
                        ? subPlan.offName() + " -> " + subPlan.onName()
                        : "substitution";
                    yield shape + " + " + sub;
                }
            };
        }
    }

    private record PositionPlan(
        String playerId,
        String playerName,
        double xPercent,
        double yPercent,
        Map<String, LineupSlotDTO> slotsByPlayerId
    ) {}

    private record SubPlan(
        String playerOffId,
        String playerOnId,
        String offName,
        String onName,
        String offPosition,
        String onPosition,
        int scoreDelta
    ) {}

    private record LabPair(
        SessionPlayer starter,
        SessionPlayer bench
    ) {}

    private record ObjectiveContrastLabPairs(
        SessionPlayer offensiveStarter,
        SessionPlayer offensiveBench,
        SessionPlayer defensiveStarter,
        SessionPlayer defensiveBench
    ) {}

    // ========== resetRound (V24D24.3-HOTFIX) ==========

    @Override
    public Mono<Void> resetRound(UUID userId, String roundId) {
        if (roundId == null || roundId.isBlank()) {
            return Mono.error(new IllegalArgumentException("roundId is required and must be non-blank"));
        }
        log.info("[V24D24.3-HOTFIX] resetRound userId={}, roundId={}",
            userId, roundId);

        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " — call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return executeResetRound(career, roundId);
            });
    }

    /**
     * V24D24.3-HOTFIX core implementation. Resets every fixture of the
     * given round so the next {@code /match-engine/rounds/start} call
     * runs a fresh V24 simulation. Concretely, for every fixture in the
     * round we:
     *
     * <ol>
     *   <li>Call {@code fixture.reset()} — clears status=COMPLETED →
     *       PENDING and nulls the result. Without this step,
     *       {@code MatchFixture.startSimulation} (line 88-93) throws
     *       "Match can only start from PENDING state".</li>
     *   <li>Call {@code matchEngineRegistry.stopAndRemoveEngine} — evicts
     *       the cached {@code MatchSession} so the next
     *       {@code engineRegistry.startEngine} call rebuilds from
     *       scratch. Without this step, the registry returns the old
     *       session (which has already finished) and the new
     *       simulation never starts.</li>
     *   <li>Call {@code v24StoragePort.deleteByMatchId} — clears the V24
     *       detail (timeline / shot map / xG) from Redis so the next
     *       {@code GET /detail} returns the new simulation's events,
     *       not the old one.</li>
     * </ol>
     *
     * <p>The reverse-update is deliberately omitted: re-running the same
     * round does not change career-level standings here (the smoke
     * harness accepts that "standings drift" is the cost of true
     * determinism; REVISOR can call {@code replace-fixtures} for a
     * clean state if needed). This matches the documented limitation
     * of {@code replayMatch}.
     */
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
        int clearedDetails = 0;
        UUID userId = career.getUserId();

        for (MatchFixture fixture : roundFixtures) {
            String matchId = fixture.getMatchId();
            boolean wasCompleted = fixture.isCompleted();
            fixture.reset();
            resetCount++;

            // Stop & remove the cached MatchSession so the next
            // /match-engine/rounds/start gets a fresh engine.
            try {
                if (matchEngineRegistry.hasEngine(userId, UUID.fromString(matchId))) {
                    matchEngineRegistry.stopAndRemoveEngine(userId, UUID.fromString(matchId));
                    removedEngines++;
                }
            } catch (Exception e) {
                log.warn("[V24D24.3-HOTFIX] resetRound: failed to remove engine for matchId={}: {}",
                    matchId, e.getMessage());
            }

            // Clear the old V24 detail from Redis (best-effort — a
            // Redis failure logs a warning but does NOT fail the reset,
            // so a stale Redis state is preferable to blocking the
            // smoke flow).
            try {
                v24StoragePort.deleteByMatchId(careerId, matchId);
                clearedDetails++;
            } catch (Exception e) {
                log.warn("[V24D24.3-HOTFIX] resetRound: failed to clear V24 detail for matchId={}: {}",
                    matchId, e.getMessage());
            }

            log.info("[V24D24.3-HOTFIX] resetRound matchId={} round={} wasCompleted={}",
                matchId, fixture.getRound(), wasCompleted);
        }

        // V24D24.3-HOTFIX (BUG_ORCHESTRATOR_SKIPS_NON_CURRENT_ROUND):
        // The MatchSimulationOrchestrator.processResultsInternal early-returns
        // when `firstFixture.getRound() != careerCurrentRound` (line 126-128 of
        // MatchSimulationOrchestrator.java). After running several rounds, the
        // career's `currentRound` may be 4 while the manager is now re-simulating
        // round 1 — the orchestrator would silently skip the result-persistence
        // and the fixtures would stay PENDING in Mongo (despite the V24 detail
        // being updated in Redis).
        //
        // Fix: rewind `currentRound` to the round being re-simulated so the
        // orchestrator's round-equality guard passes. The test-harness is the
        // ONLY caller of this method (it's @Profile-gated) and the manager
        // accepts the "rewind" cost (it just means the next natural advance
        // step will be `round + 1` instead of whatever the previous current
        // was — the smoke accepts that the tournament may finish earlier than
        // the stored totalRounds on a rewind-reset).
        int previousCurrentRound = career.getTournamentState().getCurrentRound();
        if (previousCurrentRound != round) {
            log.info("[V24D24.3-HOTFIX] resetRound rewinding currentRound: {} -> {} "
                + "(orchestrator only processes currentRound={} matchResults)",
                previousCurrentRound, round, round);
            career.getTournamentState().setCurrentRound(round);
        }
        // Force the career back into PRE_MATCH so the round engine is allowed
        // to start a new round. (The orchestrator would normally set
        // careerPhase=WAITING_USER after a round finishes, blocking a re-run.)
        career.getTournamentState().setCareerPhase(
            com.footballmanager.domain.model.entity.CareerPhase.PRE_MATCH);

        log.info("[V24D24.3-HOTFIX] resetRound complete careerId={} roundId={} round={} resetFixtures={} removedEngines={} clearedDetails={} rewoundFrom={}",
            careerId, roundId, round, resetCount, removedEngines, clearedDetails, previousCurrentRound);

        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())));
    }

    /**
     * V24D24.3-HOTFIX: roundId is a deterministic UUID derived from
     * (careerId, round) via {@code FixtureQueryHelper.deriveRoundId}.
     * Since we don't have a direct lookup index for roundId, we
     * recover the round number by enumerating all possible rounds and
     * matching the UUID. With {@code totalRounds <= 38} (typical
     * tournament) this is cheap.
     *
     * <p>If we ever extend the tournament beyond 38 rounds, this should
     * be replaced with a direct UUID→round index or by storing the
     * roundId on the fixture.
     *
     * @return 1-based round number, or -1 if no match found
     */
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
            log.warn("[V24D24.3-HOTFIX] deriveRoundFromUuid failed for roundId={}, careerId={}: {}",
                roundId, careerId, e.getMessage());
            return -1;
        }
    }

    private List<V24MatchLineupPlayerDto> lineupSnapshot(List<SessionPlayer> players) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }
        return players.stream()
            .filter(java.util.Objects::nonNull)
            .map(V24MatchLineupPlayerDto::fromSessionPlayer)
            .toList();
    }

    private record PlayerSwapAutoPair(
        SessionPlayer starter,
        SessionPlayer bench
    ) {}
}
