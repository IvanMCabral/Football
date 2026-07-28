package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.testharness.ScenarioMatrixRow;
import com.footballmanager.domain.port.in.testharness.ScenarioMatrixSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
class TestHarnessScenarioMatrixService {

    private static final String AUTO_POSITION_PIXEL_PREFIX = "__AUTO_";

    private final CareerRepository careerRepository;
    private final V24MatchContextFactory v24ContextFactory;
    private final FormationService formationService = new FormationService();

    private Mono<CareerSave> loadLiveCareer(UUID userId) {
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> optionalCareer
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new IllegalStateException(
                    "Career not found for userId=" + userId))));
    }

    Mono<List<ScenarioMatrixRow>> runScenarioMatrix(UUID userId, String matchId, Long seedOverride) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        long seed = (seedOverride != null) ? seedOverride : 12345L;

        return loadLiveCareer(userId)
            .flatMap(career -> Mono.fromSupplier(() -> executeScenarioMatrix(career, matchId, seed)));
    }

    Mono<List<ScenarioMatrixRow>> runScenarioMatrix(CareerSave career, String matchId, Long seedOverride) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        if (career == null) {
            return Mono.error(new IllegalArgumentException("career is required"));
        }
        long seed = (seedOverride != null) ? seedOverride : 12345L;
        return Mono.fromSupplier(() -> executeScenarioMatrix(career, matchId, seed));
    }

    Mono<List<ScenarioMatrixSummaryRow>> runScenarioMatrixSummary(
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
        String controlledTeamId = TestHarnessCommonSupport.resolveControlledTeamId(career, fixture, controlledTeamSide);
        boolean controlledIsHome = fixture.getHomeTeamId().equals(controlledTeamId);

        Map<String, TestHarnessScenarioSummaryAccumulator> accumulators = new LinkedHashMap<>();
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
                String baselineKey = TestHarnessScenarioFilterSupport.baselineScenarioFor(row);
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
                        new TestHarnessScenarioSummaryAccumulator(row.scenario(), row.actionType(), row.actionDetail(), finalBaselineKey))
                    .add(row, baseline, controlledIsHome);
            }
        }
        return accumulators.values().stream()
            .map(TestHarnessScenarioSummaryAccumulator::toRow)
            .toList();
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

        String formation = TestHarnessCommonSupport.currentFormation(career, userTeamId, userIsHome ? home : away);
        String normalizedScenarioGroup = TestHarnessScenarioFilterSupport.normalizeScenarioGroup(scenarioGroup);
        List<ScenarioMatrixRow> rows = new ArrayList<>();
        rows.add(TestHarnessScenarioRunner.run(career, fixture, home, away, userTeamId, seed,
            "base-balanced", "Base: full match BALANCED", formation,
            TeamStyle.BALANCED, null, ScenarioAction.none(), v24ContextFactory));
        rows.add(TestHarnessScenarioRunner.run(career, fixture, home, away, userTeamId, seed,
            "m45-noop-replay", "Minute 45 -> replay without tactical change", formation,
            TeamStyle.BALANCED, 45, ScenarioAction.noopReplay(), v24ContextFactory));
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
        TestHarnessScenarioFilterSupport.buildFormationScenarioAction(userStarters, "4-4-2", formationService)
            .ifPresent(action -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
                "m45-formation-442", "Minute 45 -> formation 4-4-2 with visual slots", formation,
                TeamStyle.BALANCED, 45, action));
        TestHarnessScenarioFilterSupport.buildFormationScenarioAction(userStarters, "4-3-3", formationService)
            .ifPresent(action -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
                "m45-formation-433", "Minute 45 -> formation 4-3-3 with visual slots", formation,
                TeamStyle.BALANCED, 45, action));
        TestHarnessScenarioFilterSupport.buildFormationScenarioAction(userStarters, "4-2-3-1", formationService)
            .ifPresent(action -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
                "m45-formation-4231", "Minute 45 -> formation 4-2-3-1 with visual slots", formation,
                TeamStyle.BALANCED, 45, action));
        Optional<PositionPlan> advancedMid = TestHarnessScenarioPlanSupport.chooseMidfielderPositionPlan(baseContext, userTeamId, 50.0, 40.0);
        advancedMid.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-position-mid-up",
            "Minute 45 -> move " + plan.playerName() + " to x50/y40",
            formation,
            TeamStyle.BALANCED,
            45,
            ScenarioAction.position(plan)));
        Optional<PositionPlan> advancedMidOneMore = TestHarnessScenarioPlanSupport.chooseMidfielderPositionPlan(baseContext, userTeamId, 50.0, 39.0);
        advancedMidOneMore.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-position-mid-up-1px",
            "Minute 45 -> move " + plan.playerName() + " to x50/y39",
            formation,
            TeamStyle.BALANCED,
            45,
            ScenarioAction.position(plan)));
        Optional<PositionPlan> wideMid = TestHarnessScenarioPlanSupport.chooseMidfielderPositionPlan(baseContext, userTeamId, 18.0, 50.0);
        wideMid.ifPresent(plan -> addScenarioIfRequested(rows, normalizedScenarioGroup, career, fixture, home, away, userTeamId, seed,
            "m45-position-mid-wide",
            "Minute 45 -> move " + plan.playerName() + " wide x18/y50",
            formation,
            TeamStyle.BALANCED,
            45,
            ScenarioAction.position(plan)));

        Optional<PositionPlan> compactCenterPlan =
            TestHarnessScenarioPlanSupport.buildShapePlan(baseContext, userTeamId, formation, "compact-center", ShapePreset.COMPACT_CENTER);
        Optional<PositionPlan> wideOverloadPlan =
            TestHarnessScenarioPlanSupport.buildShapePlan(baseContext, userTeamId, formation, "wide-overload", ShapePreset.WIDE_OVERLOAD);
        Optional<PositionPlan> attackingStepPlan =
            TestHarnessScenarioPlanSupport.buildShapePlan(baseContext, userTeamId, formation, "attacking-step", ShapePreset.ATTACKING_STEP);
        Optional<PositionPlan> attackingHighPlan =
            TestHarnessScenarioPlanSupport.buildShapePlan(baseContext, userTeamId, formation, "attacking-high", ShapePreset.ATTACKING_HIGH);
        Optional<PositionPlan> highPressPlan =
            TestHarnessScenarioPlanSupport.buildShapePlan(baseContext, userTeamId, formation, "high-press", ShapePreset.HIGH_PRESS);
        Optional<PositionPlan> doubleStrikerPlan =
            TestHarnessScenarioPlanSupport.buildShapePlan(baseContext, userTeamId, formation, "double-striker", ShapePreset.DOUBLE_STRIKER);
        Optional<PositionPlan> allOutPlan =
            TestHarnessScenarioPlanSupport.buildShapePlan(baseContext, userTeamId, formation, "all-out", ShapePreset.ALL_OUT);
        Optional<PositionPlan> defensiveStepPlan =
            TestHarnessScenarioPlanSupport.buildShapePlan(baseContext, userTeamId, formation, "defensive-step", ShapePreset.DEFENSIVE_STEP);
        Optional<PositionPlan> defensiveLowPlan =
            TestHarnessScenarioPlanSupport.buildShapePlan(baseContext, userTeamId, formation, "defensive-low", ShapePreset.DEFENSIVE_LOW);
        Optional<PositionPlan> leftOverloadPlan =
            TestHarnessScenarioPlanSupport.buildShapePlan(baseContext, userTeamId, formation, "left-overload", ShapePreset.LEFT_OVERLOAD);
        Optional<PositionPlan> rightOverloadPlan =
            TestHarnessScenarioPlanSupport.buildShapePlan(baseContext, userTeamId, formation, "right-overload", ShapePreset.RIGHT_OVERLOAD);

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

        Optional<SubPlan> impactSub = TestHarnessScenarioPlanSupport.chooseImpactSubstitution(v24ContextFactory, career, fixture, userTeamId, home, away);
        Optional<SubPlan> upgradeSub = TestHarnessScenarioPlanSupport.chooseScoredSubstitution(v24ContextFactory, career, fixture, userTeamId, home, away, true);
        Optional<SubPlan> downgradeSub = TestHarnessScenarioPlanSupport.chooseScoredSubstitution(v24ContextFactory, career, fixture, userTeamId, home, away, false);
        Optional<SubPlan> offensiveUpgradeSub = TestHarnessScenarioPlanSupport.chooseScoredSubstitution(
            v24ContextFactory, career, fixture, userTeamId, home, away, true, Set.of("ATT", "WINGER"));
        Optional<SubPlan> offensiveDowngradeSub = TestHarnessScenarioPlanSupport.chooseScoredSubstitution(
            v24ContextFactory, career, fixture, userTeamId, home, away, false, Set.of("ATT", "WINGER"));
        Optional<SubPlan> defensiveUpgradeSub = TestHarnessScenarioPlanSupport.chooseScoredSubstitution(
            v24ContextFactory, career, fixture, userTeamId, home, away, true, Set.of("DEF"));
        Optional<SubPlan> defensiveDowngradeSub = TestHarnessScenarioPlanSupport.chooseScoredSubstitution(
            v24ContextFactory, career, fixture, userTeamId, home, away, false, Set.of("DEF"));

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
            rows.add(TestHarnessScenarioRunner.run(career, fixture, home, away, userTeamId, seed,
            "m30-noop-replay",
            "Minute 30 -> replay without tactical change",
            formation,
            TeamStyle.BALANCED,
            30,
            ScenarioAction.noopReplay(),
            v24ContextFactory));
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
            rows.add(TestHarnessScenarioRunner.run(career, fixture, home, away, userTeamId, seed,
            "m60-noop-replay",
            "Minute 60 -> replay without tactical change",
            formation,
            TeamStyle.BALANCED,
            60,
            ScenarioAction.noopReplay(),
            v24ContextFactory));
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
            && !TestHarnessScenarioFilterSupport.scenarioMatchesGroup(scenario, normalizedScenarioGroup)) {
            return;
        }
        rows.add(TestHarnessScenarioRunner.run(career, fixture, home, away, userTeamId, seed,
            scenario, description, formation, baseUserStyle, changeMinute, action, v24ContextFactory));
    }

}

