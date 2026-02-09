package com.footballmanager.application.service.query;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.TournamentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Servicio de consultas para palmares historico.
 */
@Slf4j
@Service
public class CareerPalmaresQueryService {

    public record PalmaresEntry(
            Integer season,
            String divisionId,
            String divisionName,
            String championTeamId,
            String championTeamName,
            String championCoachName
    ) {}

    public record TopTeamEntry(
            String teamId,
            String teamName,
            String coachName,
            Integer titles
    ) {}

    public record PromotionEntry(
            String teamId,
            String teamName,
            String fromDivisionId,
            String fromDivisionName,
            String toDivisionId,
            String toDivisionName,
            String type,
            Integer fromPosition
    ) {}

    /**
     * Obtiene el palmares de la division del usuario.
     */
    public Mono<List<PalmaresEntry>> getUserDivisionPalmares(CareerSave career) {
        return Mono.fromCallable(() -> {
            var userDivision = career.getUserDivision();
            String userDivId = userDivision != null ? userDivision.getDivisionId() : null;

            // LOG: Tracing palmares duplication
            int rawPalmaresSize = career.getSeasonManager().getPalmares() != null ? career.getSeasonManager().getPalmares().size() : 0;
            log.info("[PALMARES-TRACE] getUserDivisionPalmares() - userDivId={}, rawPalmaresSize={}",
                userDivId, rawPalmaresSize);

            List<PalmaresEntry> result = career.getSeasonManager().getPalmares().stream()
                    .filter(r -> userDivId == null || userDivId.equals(r.getDivisionId()))
                    .map(this::toPalmaresEntry)
                    .toList();

            log.info("[PALMARES-TRACE] getUserDivisionPalmares() - returning {} entries", result.size());
            return result;
        });
    }

    /**
     * Obtiene el palmares completo de todas las divisiones.
     */
    public Mono<List<PalmaresEntry>> getAllPalmares(CareerSave career) {
        return Mono.fromCallable(() -> {
            // LOG: Tracing palmares duplication
            int rawPalmaresSize = career.getSeasonManager().getPalmares() != null ? career.getSeasonManager().getPalmares().size() : 0;
            log.info("[PALMARES-TRACE] getAllPalmares() - careerId={}, rawPalmaresSize={}",
                career.getUserId(), rawPalmaresSize);

            if (career.getSeasonManager().getPalmares() != null) {
                career.getSeasonManager().getPalmares().forEach(tr ->
                    log.debug("[PALMARES-TRACE]   Palmares entry: season={}, division={}, champion={}",
                        tr.getSeason(), tr.getDivisionId(), tr.getChampionTeamName())
                );
            }

            List<PalmaresEntry> result = career.getSeasonManager().getPalmares().stream()
                    .map(this::toPalmaresEntry)
                    .toList();

            log.info("[PALMARES-TRACE] getAllPalmares() - returning {} entries", result.size());
            return result;
        });
    }

    /**
     * Obtiene los equipos con mas titulos.
     */
    public Mono<List<TopTeamEntry>> getTopTeams(CareerSave career) {
        return Mono.fromCallable(() -> {
            List<TopTeamEntry> result = career.getSeasonManager().getTopTeams().stream()
                    .map(tc -> new TopTeamEntry(
                            tc.getTeamId(),
                            tc.getTeamName(),
                            tc.getCoachName(),
                            tc.getTitles()
                    ))
                    .toList();
            log.info("[TOPTEAMS-API] getTopTeams() - returning {} entries", result.size());
            return result;
        });
    }

    /**
     * Obtiene el palmares filtrado por division especifica.
     */
    public Mono<List<PalmaresEntry>> getPalmaresByDivision(CareerSave career, String divisionId) {
        return Mono.fromCallable(() -> {
            List<PalmaresEntry> result = career.getSeasonManager().getPalmares().stream()
                    .filter(r -> divisionId == null || divisionId.equals(r.getDivisionId()))
                    .map(this::toPalmaresEntry)
                    .toList();
            log.info("[PALMARES-API] getPalmaresByDivision() - divisionId={}, entries={}", divisionId, result.size());
            return result;
        });
    }

    /**
     * Obtiene los equipos con mas titulos filtrados por division.
     */
    public Mono<List<TopTeamEntry>> getTopTeamsByDivision(CareerSave career, String divisionId) {
        return Mono.fromCallable(() -> {
            // Filtrar los palmares por division y contar titulos por equipo
            var titleCountMap = new java.util.HashMap<String, TopTeamEntry>();

            career.getSeasonManager().getPalmares().stream()
                    .filter(r -> divisionId == null || divisionId.equals(r.getDivisionId()))
                    .forEach(result -> {
                        String teamId = result.getChampionTeamId();
                        TopTeamEntry existing = titleCountMap.get(teamId);

                        if (existing == null) {
                            // Primer título para este equipo
                            titleCountMap.put(teamId, new TopTeamEntry(
                                result.getChampionTeamId(),
                                result.getChampionTeamName(),
                                result.getChampionCoachName(),
                                1
                            ));
                        } else {
                            // Incrementar títulos
                            titleCountMap.put(teamId, new TopTeamEntry(
                                existing.teamId(),
                                existing.teamName(),
                                existing.coachName(),
                                existing.titles() + 1
                            ));
                        }
                    });

            List<TopTeamEntry> result = titleCountMap.values().stream()
                    .sorted((a, b) -> Integer.compare(b.titles(), a.titles()))
                    .toList();

            log.info("[TOPTEAMS-API] getTopTeamsByDivision() - divisionId={}, entries={}", divisionId, result.size());
            return result;
        });
    }

    /**
     * Obtiene las promociones y descensos de la ultima temporada ejecutados.
     */
    public Mono<List<PromotionEntry>> getPromotions(CareerSave career) {
        return Mono.fromCallable(() -> {
            return career.getSeasonManager().getLastExecutedPromotions().stream()
                    .map(p -> new PromotionEntry(
                            p.getTeamId(),
                            p.getTeamName(),
                            p.getFromDivisionId(),
                            p.getFromDivisionName(),
                            p.getToDivisionId(),
                            p.getToDivisionName(),
                            p.getType().name(),
                            p.getFromPosition()
                    ))
                    .toList();
        });
    }

    private PalmaresEntry toPalmaresEntry(TournamentResult result) {
        return new PalmaresEntry(
                result.getSeason(),
                result.getDivisionId(),
                result.getDivisionName() != null ? result.getDivisionName() : "Division",
                result.getChampionTeamId(),
                result.getChampionTeamName(),
                result.getChampionCoachName()
        );
    }
}
