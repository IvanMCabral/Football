package com.footballmanager.application.service.world;

/**
 * V25D78-C55.1: Registry of the 10 leagues supported by {@link WorldSeedService}.
 *
 * <p>Each league has a slug (used in URL endpoints like {@code /world/seed-{slug}}),
 * a display name (used in the UI dropdown), and the classpath resource path
 * pointing to the seed JSON file. The JSON files follow the {@link LaLigaSeedData}
 * schema (same as C44).
 *
 * <p><b>Source data is fully synthetic</b> (algorithmic team names + player
 * stats, see {@code scripts/generate_seed_files.py}). The 10 leagues together
 * cover the "Top 10 mundial" set requested by Iván in C55.0.
 *
 * <p>Adding a new league = 3 things: append the enum value, add the JSON file
 * under {@code src/main/resources/seed/}, regenerate it via the Python script.
 */
public enum LeagueType {

    LALIGA("laliga", "La Liga", "seed/laliga-2024-25.json"),
    PREMIER("premier", "Premier League", "seed/premier-2024-25.json"),
    BUNDESLIGA("bundesliga", "Bundesliga", "seed/bundesliga-2024-25.json"),
    SERIE_A("seria-a", "Serie A", "seed/seria-a-2024-25.json"),
    LIGUE_1("ligue-1", "Ligue 1", "seed/ligue-1-2024-25.json"),
    BRASILEIRAO("brasileirao", "Brasileirão", "seed/brasileirao-2024.json"),
    LIGA_PROFESIONAL("liga-profesional", "Liga Profesional", "seed/liga-profesional-2024.json"),
    MLS("mls", "Major League Soccer", "seed/mls-2024.json"),
    EREDIVISIE("eredivisie", "Eredivisie", "seed/eredivisie-2024-25.json"),
    CHAMPIONSHIP("championship", "EFL Championship", "seed/championship-2024.json");

    private final String slug;
    private final String displayName;
    private final String resourcePath;

    LeagueType(String slug, String displayName, String resourcePath) {
        this.slug = slug;
        this.displayName = displayName;
        this.resourcePath = resourcePath;
    }

    public String slug() { return slug; }
    public String displayName() { return displayName; }
    public String resourcePath() { return resourcePath; }

    /**
     * Returns the {@link LeagueType} whose slug matches {@code slug}, or null if
     * no such league exists. Used by the admin endpoint to map slug → enum.
     */
    public static LeagueType fromSlug(String slug) {
        if (slug == null) return null;
        for (LeagueType lt : values()) {
            if (lt.slug.equalsIgnoreCase(slug)) return lt;
        }
        return null;
    }
}