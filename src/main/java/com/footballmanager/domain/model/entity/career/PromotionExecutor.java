package com.footballmanager.domain.model.entity.career;

import com.footballmanager.domain.model.entity.Division;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Ejecuta ascensos y descensos de equipos.
 * Mantiene el historial de promociones ejecutadas.
 */
@Slf4j
public class PromotionExecutor {

    private final List<Promotion> lastExecutedPromotions = new ArrayList<>();

    public PromotionExecutor() {
    }

    /**
     * Ejecuta las promociones calculadas.
     * @param promotions Lista de promociones a ejecutar
     * @param divisionManager Gestor de divisiones para mover equipos
     */
    public void execute(List<Promotion> promotions, DivisionManager divisionManager) {
        if (promotions.isEmpty()) {
            log.info("[PROMOTIONS] No promotions/relegations to execute");
            return;
        }

        // Guardar historial (con deduplicación)
        lastExecutedPromotions.clear();
        deduplicateAndAdd(promotions);

        // Primero procesar ascensos
        for (Promotion p : promotions) {
            if (p.getType() == Promotion.PromotionType.PROMOTED) {
                executePromotion(p, divisionManager);
            }
        }

        // Luego procesar descensos
        for (Promotion p : promotions) {
            if (p.getType() == Promotion.PromotionType.RELEGATED) {
                executeRelegation(p, divisionManager);
            }
        }

        log.info("[PROMOTIONS] Executed {} promotions/relegations", promotions.size());
    }

    private void deduplicateAndAdd(List<Promotion> promotions) {
        for (Promotion p : promotions) {
            boolean exists = lastExecutedPromotions.stream()
                .anyMatch(existing ->
                    existing.getTeamId().equals(p.getTeamId()) &&
                    existing.getType() == p.getType());
            if (!exists) {
                lastExecutedPromotions.add(p);
            }
        }
    }

    private void executePromotion(Promotion promotion, DivisionManager divisionManager) {
        Division fromDivision = divisionManager.findDivisionById(promotion.getFromDivisionId());
        Division toDivision = divisionManager.findDivisionById(promotion.getToDivisionId());

        if (fromDivision != null && toDivision != null) {
            divisionManager.moveTeam(promotion.getTeamId(), fromDivision, toDivision);
            log.debug("[PROMOTIONS] Promoted: {}", promotion.getTeamName());
        }
    }

    private void executeRelegation(Promotion promotion, DivisionManager divisionManager) {
        Division fromDivision = divisionManager.findDivisionById(promotion.getFromDivisionId());
        Division toDivision = divisionManager.findDivisionById(promotion.getToDivisionId());

        if (fromDivision != null && toDivision != null) {
            divisionManager.moveTeam(promotion.getTeamId(), fromDivision, toDivision);
            log.debug("[PROMOTIONS] Relegated: {}", promotion.getTeamName());
        }
    }

    public List<Promotion> getLastExecutedPromotions() {
        return lastExecutedPromotions;
    }
}
