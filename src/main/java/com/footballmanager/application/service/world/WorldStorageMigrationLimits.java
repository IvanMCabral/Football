package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.entity.WorldLeague;

/** Bounded admission contract for legacy owners; it does not alter gameplay. */
public final class WorldStorageMigrationLimits {

    public static final int MAX_CUSTOM_TEAMS = 128;
    public static final int MAX_CUSTOM_PLAYERS = 4_096;
    public static final int MAX_TOTAL_TEAMS = 512;
    public static final int MAX_TOTAL_PLAYERS = 16_384;
    public static final int MAX_ALIASES = 8_192;
    public static final int MAX_SPECIAL_TRAITS_PER_PLAYER = 32;
    public static final int MAX_SKILLS_PER_PLAYER = com.footballmanager.domain.model.valueobject.PlayerSkill.values().length;
    public static final int MAX_NAME_CHARS = 512;
    public static final int MAX_IDENTIFIER_CHARS = 128;
    public static final int MAX_LEAGUES = 256;
    public static final int MAX_SERIALIZED_LEGACY_BYTES = 16 * 1024 * 1024;

    private WorldStorageMigrationLimits() { }

    public static Validation validate(WorldSnapshot snapshot) {
        return validate(snapshot, 0);
    }

    public static Validation validate(WorldSnapshot snapshot, int serializedBytes) {
        if (snapshot == null) return Validation.invalid("world snapshot is missing");
        if (serializedBytes < 0 || serializedBytes > MAX_SERIALIZED_LEGACY_BYTES) {
            return Validation.invalid("serialized legacy world limit exceeded");
        }
        if (snapshot.getAllWorldTeams().size() > MAX_TOTAL_TEAMS) {
            return Validation.invalid("total team limit exceeded");
        }
        if (snapshot.getAllWorldPlayers().size() > MAX_TOTAL_PLAYERS) {
            return Validation.invalid("total player limit exceeded");
        }
        long customTeams = snapshot.getAllWorldTeams().stream()
                .filter(team -> team.getOrigin() == WorldTeam.WorldTeamOrigin.CUSTOM).count();
        long customPlayers = snapshot.getAllWorldPlayers().stream()
                .filter(player -> player.getOrigin() != WorldPlayer.WorldPlayerOrigin.REAL).count();
        int aliases = snapshot.getWorldPlayerAliases().size();
        int leagues = snapshot.getLeagues() == null ? 0 : snapshot.getLeagues().size();
        if (customTeams > MAX_CUSTOM_TEAMS) return Validation.invalid("custom team limit exceeded");
        if (customPlayers > MAX_CUSTOM_PLAYERS) return Validation.invalid("custom player limit exceeded");
        if (aliases > MAX_ALIASES) return Validation.invalid("legacy alias limit exceeded");
        if (leagues > MAX_LEAGUES) return Validation.invalid("league limit exceeded");
        boolean invalidTeamText = snapshot.getAllWorldTeams().stream().anyMatch(team ->
                tooLong(team.getName()) || tooLong(team.getCountry()) || tooLong(team.getCity())
                        || tooLong(team.getBaseFormation()) || invalidId(team.getWorldTeamId()));
        boolean invalidPlayerText = snapshot.getAllWorldPlayers().stream().anyMatch(player ->
                tooLong(player.getName()) || tooLong(player.getPosition())
                        || invalidId(player.getWorldPlayerId()) || invalidId(player.getWorldTeamId()));
        boolean invalidLeagueText = snapshot.getLeagues() != null && snapshot.getLeagues().stream()
                .anyMatch(WorldStorageMigrationLimits::invalidLeague);
        boolean excessivePlayerMetadata = snapshot.getAllWorldPlayers().stream().anyMatch(player ->
                player.getSpecialTraits().size() > MAX_SPECIAL_TRAITS_PER_PLAYER
                        || player.getSkillLevels().size() > MAX_SKILLS_PER_PLAYER);
        boolean invalidAlias = snapshot.getWorldPlayerAliases().entrySet().stream()
                .anyMatch(entry -> invalidId(entry.getKey()) || invalidId(entry.getValue()));
        if (invalidTeamText || invalidPlayerText || invalidLeagueText || invalidAlias) {
            return Validation.invalid("world text or identifier limit exceeded");
        }
        if (excessivePlayerMetadata) return Validation.invalid("player metadata limit exceeded");
        return new Validation(true, "within migration bounds");
    }

    private static boolean invalidLeague(WorldLeague league) {
        return league == null || tooLong(league.getName()) || tooLong(league.getCountry());
    }

    private static boolean tooLong(String value) {
        return value != null && value.length() > MAX_NAME_CHARS;
    }

    private static boolean invalidId(String value) {
        return value != null && value.length() > MAX_IDENTIFIER_CHARS;
    }

    public record Validation(boolean valid, String reason) {
        static Validation invalid(String reason) { return new Validation(false, reason); }
    }
}
