package com.footballmanager.application.service.testharness;

import com.footballmanager.adapters.in.web.career.lineup.dto.FormationDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationPositionDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngine;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24LiveSession;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.application.service.simulation.v24.V24PlayerMatchRatingDto;
import com.footballmanager.application.service.simulation.v24.V24ShotLocation;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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

        log.info("[V24D20-TESTHARNESS] resetInjuries userId={} squadSize={} cleared={}",
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

        log.info("[V24D20-TESTHARNESS] setFormation userId={} team={} formation={}",
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
            return firstSquadDefenders(squad, limit);
        }

        Map<String, SessionPlayer> byId = squad.stream()
            .filter(p -> p != null && p.getSessionPlayerId() != null)
            .collect(Collectors.toMap(SessionPlayer::getSessionPlayerId, p -> p, (a, b) -> a));

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

        return slots.values().stream()
            .filter(slot -> slot != null && slot.playerId() != null)
            .map(slot -> byId.get(slot.playerId()))
            .filter(p -> p != null && "DEF".equals(p.getPosition()))
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

        log.info("[V24D20-SANDBOX-V2-MVP] replayMatch userId={}, matchId={}, seed={}",
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
                List.<V24PlayerMatchRatingDto>of()
            );

            v24StoragePort.save(careerId, newDetail);
            log.info("[V24D21-SANDBOX-V2-MVP] replayMatch: persisted new V24 detail "
                + "for matchId={}, careerId={}, homeGoals={}, awayGoals={}",
                matchId, careerId, result.homeGoals(), result.awayGoals());
        } catch (Exception e) {
            log.warn("[V24D21-SANDBOX-V2-MVP] replayMatch: failed to persist new V24 "
                + "detail for matchId={}, continuing (replay is best-effort): {}",
                matchId, e.getMessage());
        }

        log.info("[V24D20-SANDBOX-V2-MVP] replayMatch complete: matchId={}, "
            + "newResult=({}-{}), seed={}",
            matchId, result.homeGoals(), result.awayGoals(), seed);

        // 6. Persist + invalidate cache (same pattern as the other endpoints)
        return careerRepository.save(career)
            .then(Mono.fromRunnable(() ->
                careerSessionService.invalidateCache(career.getUserId())))
            .thenReturn(fixture);
    }

    @Override
    public Mono<List<FormationMatrixRow>> runFormationMatrix(UUID userId, String matchId, Long seedOverride) {
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
                return Mono.fromSupplier(() -> executeFormationMatrix(optionalCareer.get(), matchId, seed));
            });
    }

    private List<FormationMatrixRow> executeFormationMatrix(CareerSave career, String matchId, long seed) {
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

        String userTeamId = career.getUserSessionTeamId();
        boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
        boolean userIsAway = fixture.getAwayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException(
                "Formation matrix requires a match involving the user team: " + userTeamId);
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
        List<SessionPlayer> userStarters = userIsHome
            ? baseContext.homeStartingPlayers()
            : baseContext.awayStartingPlayers();
        List<SessionPlayer> userBench = userIsHome
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
                .withNewFormation(userTeamId, formation.name())
                .withSlots(userTeamId, slots);
            V24DetailedMatchEngine.TacticalShapeDebug shapeDebug = engine.debugTacticalShape(
                userIsHome ? home : away,
                userStarters,
                userBench,
                userIsHome ? homeStyle : awayStyle,
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
        if (playerProfile.equals(slotProfile)) return 100;
        if ("WIDE_DEF".equals(slotProfile) && "DEF".equals(playerProfile)) return 92;
        if ("DEF".equals(slotProfile) && "WIDE_DEF".equals(playerProfile)) return 90;
        if ("WIDE_ATT".equals(slotProfile) && "ATT".equals(playerProfile)) return 88;
        if ("ATT".equals(slotProfile) && "WIDE_ATT".equals(playerProfile)) return 86;
        if ("AM".equals(slotProfile) && ("MID".equals(playerProfile) || "WIDE_ATT".equals(playerProfile))) return 82;
        if ("MID".equals(slotProfile) && ("DM".equals(playerProfile) || "AM".equals(playerProfile))) return 80;
        if ("DM".equals(slotProfile) && ("MID".equals(playerProfile) || "DEF".equals(playerProfile))) return 78;
        if ("WIDE_MID".equals(slotProfile) && ("MID".equals(playerProfile) || "WIDE_ATT".equals(playerProfile) || "WIDE_DEF".equals(playerProfile))) return 76;
        if ("MID".equals(slotProfile) && "WIDE_MID".equals(playerProfile)) return 74;
        if ("ATT".equals(slotProfile) && "AM".equals(playerProfile)) return 70;
        if ("AM".equals(slotProfile) && "ATT".equals(playerProfile)) return 68;
        if ("DEF".equals(slotProfile) && "DM".equals(playerProfile)) return 66;
        if ("DM".equals(slotProfile) && "WIDE_DEF".equals(playerProfile)) return 62;
        if ("MID".equals(slotProfile) && ("DEF".equals(playerProfile) || "ATT".equals(playerProfile))) return 52;
        if ("DEF".equals(slotProfile) && "MID".equals(playerProfile)) return 48;
        if ("ATT".equals(slotProfile) && "MID".equals(playerProfile)) return 48;
        if ("GK".equals(slotProfile) || "GK".equals(playerProfile)) return 0;
        return 35;
    }

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
            int seedCount) {
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
                    executeFormationMatrixSummary(optionalCareer.get(), matchId, seedStart, seedCount));
            });
    }

    private List<FormationMatrixSummaryRow> executeFormationMatrixSummary(
            CareerSave career,
            String matchId,
            long seedStart,
            int seedCount) {
        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Match not found in current tournament: " + matchId));
        String userTeamId = career.getUserSessionTeamId();
        boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
        boolean userIsAway = fixture.getAwayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException(
                "Formation matrix summary requires a match involving the user team: " + userTeamId);
        }

        Map<String, FormationSummaryAccumulator> byFormation = new LinkedHashMap<>();
        for (int i = 0; i < seedCount; i++) {
            long seed = seedStart + i;
            for (FormationMatrixRow row : executeFormationMatrix(career, matchId, seed)) {
                byFormation
                    .computeIfAbsent(row.formation(), FormationSummaryAccumulator::new)
                    .add(row, userIsHome);
            }
        }

        return byFormation.values().stream()
            .map(acc -> acc.toRow(seedStart, seedCount))
            .toList();
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
            int seedCount) {
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
                    seedCount));
            });
    }

    private PlayerSwapMatrixSummaryRow executePlayerSwapMatrixSummary(
            CareerSave career,
            String matchId,
            String starterPlayerId,
            String benchPlayerId,
            String requestedSlotId,
            long seedStart,
            int seedCount) {

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

        String userTeamId = career.getUserSessionTeamId();
        boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
        boolean userIsAway = fixture.getAwayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException(
                "Player swap matrix requires a match involving the user team: " + userTeamId);
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

        String formation = currentFormation(career, userTeamId, userIsHome ? home : away);
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
                seededBase, userTeamId, effectiveStarterPlayerId, effectiveBenchPlayerId);
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
            int seedCount) {
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
                    seedCount));
            });
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
            int seedCount) {
        MatchFixture fixture = career.getTournamentState().getFixtures().stream()
            .filter(f -> f.getMatchId().equals(matchId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Match not found in current tournament: " + matchId));
        SessionTeam home = career.getSessionTeam(fixture.getHomeTeamId());
        SessionTeam away = career.getSessionTeam(fixture.getAwayTeamId());
        if (home == null || away == null) {
            throw new IllegalStateException("SessionTeam not found for match " + matchId);
        }
        String userTeamId = career.getUserSessionTeamId();
        boolean userIsHome = fixture.getHomeTeamId().equals(userTeamId);
        boolean userIsAway = fixture.getAwayTeamId().equals(userTeamId);
        if (!userIsHome && !userIsAway) {
            throw new IllegalArgumentException("Position pixel matrix requires a match involving the user team: " + userTeamId);
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
                userTeamId,
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
            currentFormation(career, userTeamId, userIsHome ? home : away),
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
            round3(movedAvg.rightWideXgAgainst() - baseAvg.rightWideXgAgainst()));
    }

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
                baselineScenario);
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
        addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-formation-433", "Minute 45 -> formation 4-3-3", formation,
            TeamStyle.BALANCED, 45, ScenarioAction.formation("4-3-3"));
        addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-formation-4231", "Minute 45 -> formation 4-2-3-1", formation,
            TeamStyle.BALANCED, 45, ScenarioAction.formation("4-2-3-1"));

        V24MatchContext baseContext = v24ContextFactory.buildWithStyles(
            career,
            fixture,
            home,
            away,
            userIsHome ? TeamStyle.BALANCED : home.getStyle(),
            userIsHome ? away.getStyle() : TeamStyle.BALANCED,
            seed);
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

        List.of(
            buildShapePlan(baseContext, userTeamId, "compact-center", ShapePreset.COMPACT_CENTER),
            buildShapePlan(baseContext, userTeamId, "wide-overload", ShapePreset.WIDE_OVERLOAD),
            buildShapePlan(baseContext, userTeamId, "attacking-step", ShapePreset.ATTACKING_STEP),
            buildShapePlan(baseContext, userTeamId, "attacking-high", ShapePreset.ATTACKING_HIGH),
            buildShapePlan(baseContext, userTeamId, "defensive-step", ShapePreset.DEFENSIVE_STEP),
            buildShapePlan(baseContext, userTeamId, "defensive-low", ShapePreset.DEFENSIVE_LOW),
            buildShapePlan(baseContext, userTeamId, "left-overload", ShapePreset.LEFT_OVERLOAD),
            buildShapePlan(baseContext, userTeamId, "right-overload", ShapePreset.RIGHT_OVERLOAD)
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
                session.mutateContext(ctx -> ctx.withNewFormation(userTeamId, safeAction.changedFormation()));
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
        for (SessionPlayer player : starters) {
            if (player == null || player.getSessionPlayerId() == null) continue;
            String position = player.getPosition() != null ? player.getPosition() : "MID";
            int index = positionIndex.merge(position, 1, Integer::sum) - 1;
            double[] coords = shapeCoords(position, index, preset);
            LineupSlotDTO previous = baseSlots.get(player.getSessionPlayerId());
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

    private double[] shapeCoords(String position, int index, ShapePreset preset) {
        if ("GK".equals(position)) {
            return new double[] {50.0, 94.0};
        }

        double y = switch (position) {
            case "DEF" -> 78.0;
            case "ATT" -> 18.0;
            case "WINGER" -> 30.0;
            default -> 52.0;
        };
        double x = switch (position) {
            case "DEF" -> pick(index, 18.0, 38.0, 62.0, 82.0, 50.0);
            case "ATT" -> pick(index, 42.0, 58.0, 50.0, 35.0, 65.0);
            case "WINGER" -> pick(index, 18.0, 82.0, 30.0, 70.0, 50.0);
            default -> pick(index, 24.0, 42.0, 58.0, 76.0, 50.0);
        };

        switch (preset) {
            case COMPACT_CENTER -> x = 50.0 + ((x - 50.0) * 0.35);
            case WIDE_OVERLOAD -> x = 50.0 + ((x - 50.0) * 1.25);
            case ATTACKING_STEP -> y = Math.max(8.0, y - ("DEF".equals(position) ? 3.0 : 5.0));
            case ATTACKING_HIGH -> y = Math.max(8.0, y - ("DEF".equals(position) ? 3.0 : 6.0));
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
            case LEFT_OVERLOAD -> x = clamp(x - 18.0, 8.0, 92.0);
            case RIGHT_OVERLOAD -> x = clamp(x + 18.0, 8.0, 92.0);
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
            .findFirst();
    }

    private Optional<SessionPlayer> bestBenchReplacement(SessionPlayer off, List<SessionPlayer> bench) {
        return bench.stream()
            .filter(this::isOutfieldPlayer)
            .filter(p -> p.getSessionPlayerId() != null && !p.getSessionPlayerId().isBlank())
            .filter(p -> off.getPosition() != null && off.getPosition().equals(p.getPosition()))
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
                .filter(on -> samePosition(off, on))
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
            && off.getPosition().equals(on.getPosition());
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
        if ("ATT".equals(position) || "WINGER".equals(position)) {
            return 0;
        }
        if ("MID".equals(position)) {
            return 1;
        }
        if ("DEF".equals(position)) {
            return 2;
        }
        return 3;
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
        return switch (player.getPosition()) {
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
        String position = player.getPosition().toUpperCase(Locale.ROOT);
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
        SUBSTITUTION
    }

    private enum ShapePreset {
        COMPACT_CENTER,
        WIDE_OVERLOAD,
        ATTACKING_STEP,
        ATTACKING_HIGH,
        DEFENSIVE_STEP,
        DEFENSIVE_LOW,
        LEFT_OVERLOAD,
        RIGHT_OVERLOAD
    }

    private record ScenarioAction(
        ScenarioActionType type,
        TeamStyle changedStyle,
        String changedFormation,
        PositionPlan positionPlan,
        SubPlan subPlan
    ) {
        static ScenarioAction none() {
            return new ScenarioAction(ScenarioActionType.NONE, null, null, null, null);
        }

        static ScenarioAction noopReplay() {
            return new ScenarioAction(ScenarioActionType.NOOP_REPLAY, null, null, null, null);
        }

        static ScenarioAction style(TeamStyle style) {
            return new ScenarioAction(ScenarioActionType.STYLE, style, null, null, null);
        }

        static ScenarioAction opponentStyle(TeamStyle style) {
            return new ScenarioAction(ScenarioActionType.OPPONENT_STYLE, style, null, null, null);
        }

        static ScenarioAction formation(String formation) {
            return new ScenarioAction(ScenarioActionType.FORMATION, null, formation, null, null);
        }

        static ScenarioAction position(PositionPlan positionPlan) {
            return new ScenarioAction(ScenarioActionType.POSITION, null, null, positionPlan, null);
        }

        static ScenarioAction substitution(SubPlan subPlan) {
            return new ScenarioAction(ScenarioActionType.SUBSTITUTION, null, null, null, subPlan);
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

    private record PlayerSwapAutoPair(
        SessionPlayer starter,
        SessionPlayer bench
    ) {}
}
