package com.footballmanager.domain.model.valueobject;

/**
 * V25D78-C55.2: División (tier) de un equipo dentro de una liga.
 *
 * <p>Liga con 3 tiers — equivalente a Primera/Segunda/Tercera División de
 * ligas reales como LaLiga, Premier, etc. Cada liga tiene 20 equipos en
 * PRIMERA, 20 en SEGUNDA y 20 en TERCERA (60 equipos totales). Al final de
 * cada temporada, los 3 mejores de SEGUNDA ascienden a PRIMERA y los 3
 * peores de PRIMERA descienden a SEGUNDA (lo mismo para SEGUNDA ↔ TERCERA).
 *
 * <p>Este enum es parte del aggregate {@code Team} y se persiste en la
 * tabla {@code teams} (columna {@code division}, VARCHAR(20)).
 *
 * <p>Backward-compat: {@link #defaultDivision()} devuelve PRIMERA para
 * código existente que no asigna división explícitamente.
 */
public enum Division {
    PRIMERA,
    SEGUNDA,
    TERCERA;

    /**
     * String estable para persistir en Postgres. NO usar {@code name()}
     * directamente porque cambios al nombre del enum afectarían datos.
     */
    public String persistValue() {
        return this.name();
    }

    /**
     * Parse desde string persistido. Acepta mayúsculas y minúsculas
     * (defense-in-depth para data corrupta). Devuelve PRIMERA si el
     * string es null o no matchea.
     */
    public static Division fromPersistValue(String s) {
        if (s == null) return defaultDivision();
        try {
            return Division.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultDivision();
        }
    }

    /**
     * División default para backward-compat con código que no asigna
     * división explícitamente. PRIMERA = top tier.
     */
    public static Division defaultDivision() {
        return PRIMERA;
    }

    /**
     * Indica si esta división es inmediatamente superior (mayor tier)
     * a la otra. PRIMERA > SEGUNDA > TERCERA.
     */
    public boolean isAbove(Division other) {
        return this.ordinal() < other.ordinal();
    }

    /**
     * Indica si esta división es inmediatamente inferior (menor tier)
     * a la otra.
     */
    public boolean isBelow(Division other) {
        return this.ordinal() > other.ordinal();
    }
}