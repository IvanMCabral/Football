package com.footballmanager.domain.model.entity.career;

import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.entity.TeamStandings;
import com.footballmanager.domain.model.entity.TournamentState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calcula ascensos y descensos basándose en las clasificaciones finales.
 */
@Slf4j
public class PromotionCalculator {

    private final List<Promotion> promotionList = new ArrayList<>();

    public PromotionCalculator() {
    }

    /**
     * Calcula las promociones basándose en las clasificaciones finales.
     * @param divisions Divisiones ordenadas (div 1 = más alta)
     * @param tournamentState Estado del torneo con clasificaciones
     * @return Lista de promociones calculadas
     */
    public List<Promotion> calculate(List<Division> divisions, TournamentState tournamentState) {
        promotionList.clear();

        List<Division> sortedDivisions = divisions.stream()
                .sorted(Comparator.comparing(Division::getDivisionNumber))
                .toList();

        log.info("[PROMOTIONS] Calculating for {} divisions", sortedDivisions.size());

        // ASCENSO: de división más baja a más alta (de número mayor a menor)
        // DESCENSO: de división más alta a más baja (de número menor a mayor)

        for (int i = 0; i < sortedDivisions.size(); i++) {
            Division currentDivision = sortedDivisions.get(i);
            List<TeamStandings> finalStandings = tournamentState.getDivisionFinalStandings(currentDivision.getDivisionId());

            if (finalStandings.isEmpty()) {
                log.warn("[PROMOTIONS] No final standings for division {}", currentDivision.getDivisionId());
                continue;
            }

            // ASCENSO: El campeón ASCIENDE a la división anterior (más alta)
            if (i > 0) {
                Division higherDivision = sortedDivisions.get(i - 1);
                TeamStandings champion = finalStandings.get(0);
                Promotion promotion = createPromotion(
                        champion.getTeamId(),
                        champion.getTeamName(),
                        currentDivision,
                        higherDivision,
                        Promotion.PromotionType.PROMOTED,
                        1
                );
                promotionList.add(promotion);
                log.info("[PROMOTIONS] {} ascends: {} -> {}",
                        champion.getTeamName(), currentDivision.getDisplayName(), higherDivision.getDisplayName());
            }

            // DESCENSO: El último CLASIFICADO DESCIENDE a la siguiente división
            if (i < sortedDivisions.size() - 1) {
                Division lowerDivision = sortedDivisions.get(i + 1);
                TeamStandings lastPlace = finalStandings.get(finalStandings.size() - 1);
                Promotion relegation = createPromotion(
                        lastPlace.getTeamId(),
                        lastPlace.getTeamName(),
                        currentDivision,
                        lowerDivision,
                        Promotion.PromotionType.RELEGATED,
                        finalStandings.size()
                );
                promotionList.add(relegation);
                log.info("[PROMOTIONS] {} descends: {} -> {}",
                        lastPlace.getTeamName(), currentDivision.getDisplayName(), lowerDivision.getDisplayName());
            }
        }

        log.info("[PROMOTIONS] Total calculated: {}", promotionList.size());
        return promotionList;
    }

    private Promotion createPromotion(String teamId, String teamName,
                                     Division fromDivision, Division toDivision,
                                     Promotion.PromotionType type, int position) {
        Promotion p = new Promotion();
        p.setTeamId(teamId);
        p.setTeamName(teamName);
        p.setFromDivisionId(fromDivision.getDivisionId());
        p.setFromDivisionName(fromDivision.getDisplayName());
        p.setToDivisionId(toDivision.getDivisionId());
        p.setToDivisionName(toDivision.getDisplayName());
        p.setType(type);
        p.setFromPosition(position);
        return p;
    }

    public List<Promotion> getPromotions() {
        return promotionList;
    }
}
