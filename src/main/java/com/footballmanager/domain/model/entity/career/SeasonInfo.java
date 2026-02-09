package com.footballmanager.domain.model.entity.career;

/**
 * Datos de temporada.
 * Solo estado: número de temporada y flag de promociones calculadas.
 */
public record SeasonInfo(
    int currentSeason,
    boolean promotionsCalculated
) {
    public SeasonInfo() {
        this(1, false);
    }

    public SeasonInfo withNextSeason() {
        return new SeasonInfo(currentSeason + 1, false);
    }

    public SeasonInfo withPromotionsCalculated() {
        return new SeasonInfo(currentSeason, true);
    }
}
