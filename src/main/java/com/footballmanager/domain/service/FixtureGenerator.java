package com.footballmanager.domain.service;

import com.footballmanager.domain.model.valueobject.TeamId;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FixtureGenerator {

    private final FixtureValidator validator;

    public FixtureGenerator(FixtureValidator validator) {
        this.validator = validator;
    }

    public List<FixtureRound> generate(List<TeamId> teamIds, boolean isDoubleRound) {
        if (teamIds == null || teamIds.size() < 2) {
            throw new IllegalArgumentException("Se requieren al menos 2 equipos");
        }

        List<TeamId> teams = new ArrayList<>(teamIds);
        boolean hasBye = teams.size() % 2 != 0;

        if (hasBye) {
            teams.add(null); // null representa bye
        }

        int n = teams.size();
        int roundsPerLeg = n - 1;

        List<RawMatch> idaMatches = generateIda(teams, roundsPerLeg);
        List<RawMatch> vueltaMatches = generateVuelta(idaMatches, roundsPerLeg);
        List<FixtureRound> fixture = buildFixture(idaMatches, vueltaMatches, roundsPerLeg, isDoubleRound);

        validator.validate(fixture, teamIds, hasBye, isDoubleRound);

        return fixture;
    }

    public int calculateTotalRounds(int numberOfTeams, boolean isDoubleRound) {
        if (numberOfTeams < 2) {
            throw new IllegalArgumentException("Se requieren al menos 2 equipos");
        }
        int roundsPerLeg = (numberOfTeams % 2 == 0) ? numberOfTeams - 1 : numberOfTeams;
        return isDoubleRound ? roundsPerLeg * 2 : roundsPerLeg;
    }

    private List<RawMatch> generateIda(List<TeamId> teams, int roundsPerLeg) {
        List<RawMatch> matches = new ArrayList<>();
        int n = teams.size();
        int half = n / 2;
        List<TeamId> rotation = new ArrayList<>(teams);

        for (int round = 0; round < roundsPerLeg; round++) {
            int roundNumber = round + 1;

            for (int i = 0; i < half; i++) {
                TeamId left = rotation.get(i);
                TeamId right = rotation.get(n - 1 - i);

                // Solo generar partido si ambos equipos son reales (no bye)
                if (left != null && right != null) {
                    matches.add(new RawMatch(roundNumber, left, right));
                }
            }

            if (round < roundsPerLeg - 1) {
                TeamId last = rotation.remove(n - 1);
                rotation.add(1, last);
            }
        }

        return matches;
    }

    private List<RawMatch> generateVuelta(List<RawMatch> idaMatches, int roundsPerLeg) {
        List<RawMatch> vueltaMatches = new ArrayList<>();

        for (RawMatch m : idaMatches) {
            int vueltaRound = m.round() + roundsPerLeg;
            vueltaMatches.add(new RawMatch(vueltaRound, m.away(), m.home()));
        }

        return vueltaMatches;
    }

    private List<FixtureRound> buildFixture(
            List<RawMatch> idaMatches,
            List<RawMatch> vueltaMatches,
            int roundsPerLeg,
            boolean isDoubleRound) {

        Map<Integer, List<FixtureSlot>> idaPorRonda = groupByRound(idaMatches);
        Map<Integer, List<FixtureSlot>> vueltaPorRonda = groupByRound(vueltaMatches);

        List<FixtureRound> fixture = new ArrayList<>();

        for (int i = 1; i <= roundsPerLeg; i++) {
            List<FixtureSlot> matches = idaPorRonda.getOrDefault(i, Collections.emptyList());
            fixture.add(new FixtureRound(i, matches, false));
        }

        if (isDoubleRound) {
            for (int i = 1; i <= roundsPerLeg; i++) {
                int vueltaRound = i + roundsPerLeg;
                List<FixtureSlot> matches = vueltaPorRonda.getOrDefault(vueltaRound, Collections.emptyList());
                fixture.add(new FixtureRound(vueltaRound, matches, true));
            }
        }

        return fixture;
    }

    private Map<Integer, List<FixtureSlot>> groupByRound(List<RawMatch> matches) {
        Map<Integer, List<FixtureSlot>> result = new HashMap<>();
        for (RawMatch m : matches) {
            result.computeIfAbsent(m.round(), r -> new ArrayList<>())
                  .add(new FixtureSlot(m.home(), m.away()));
        }
        return result;
    }

    public record FixtureRound(int roundNumber, List<FixtureSlot> matches, boolean isReturnLeg) {}

    public record FixtureSlot(TeamId home, TeamId away) {}

    private record RawMatch(int round, TeamId home, TeamId away) {}
}
