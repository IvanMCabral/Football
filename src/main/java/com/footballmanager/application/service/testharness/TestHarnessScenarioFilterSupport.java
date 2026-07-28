package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.port.in.testharness.ScenarioMatrixRow;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class TestHarnessScenarioFilterSupport {

    private TestHarnessScenarioFilterSupport() {
    }

    static String normalizeScenarioGroup(String scenarioGroup) {
        return scenarioGroup == null ? "" : scenarioGroup.trim().toUpperCase(Locale.ROOT);
    }

    static String baselineScenarioFor(ScenarioMatrixRow row) {
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

    static Optional<ScenarioAction> buildFormationScenarioAction(
            List<SessionPlayer> starters,
            String formationName,
            FormationService formationService) {
        if (starters == null || starters.size() != 11 || formationName == null || formationName.isBlank()) {
            return Optional.empty();
        }
        return formationService.getAllFormations().stream()
            .filter(formation -> formationName.equals(formation.name()))
            .findFirst()
            .map(formation -> ScenarioAction.formation(
                formation.name(),
                FormationMatrixSlotSupport.buildSlots(starters, formation)));
    }

    static void addScenarioIfRequested(
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
            ScenarioAction action,
            MatchContextFactory matchContextFactory) {
        if (!normalizedScenarioGroup.isBlank()
            && !"ALL".equals(normalizedScenarioGroup)
            && !scenarioMatchesGroup(scenario, normalizedScenarioGroup)) {
            return;
        }
        rows.add(TestHarnessScenarioRunner.run(career, fixture, home, away, userTeamId, seed,
            scenario, description, formation, baseUserStyle, changeMinute, action, matchContextFactory));
    }

    static boolean scenarioMatchesGroup(String scenario, String group) {
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
}
