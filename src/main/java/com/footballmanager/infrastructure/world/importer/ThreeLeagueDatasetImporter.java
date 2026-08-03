package com.footballmanager.infrastructure.world.importer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.application.service.world.importer.ThreeLeagueImportReport;
import com.footballmanager.application.service.world.PlayerSpecialAttributeSelectionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThreeLeagueDatasetImporter {

    private static final String SOURCE = "manager-mvp1-explicit";
    private static final UUID SYSTEM_USER_ID = deterministicUuid("system-user:mvp1-import");
    private static final int PLAYERS_PER_CLUB = 24;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlayerSpecialAttributeSelectionValidator specialAttributeSelectionValidator =
        new PlayerSpecialAttributeSelectionValidator();

    @Transactional(transactionManager = "threeLeagueImportTransactionManager")
    public ThreeLeagueImportReport importDataset() {
        try {
            log.info("Three-league import: preparing resources (schema managed by Flyway)");
            jdbcTemplate.execute("SET lock_timeout = '5s'");
            jdbcTemplate.execute("SET statement_timeout = '30s'");
            List<CountryRecord> countries = read("data/initial/countries.json", new TypeReference<>() {});
            List<LeagueRecord> leagues = read("data/initial/leagues.json", new TypeReference<>() {});
            List<SpecialAttributeRecord> specialAttributes =
                read("data/initial/catalogs/special-attributes.json", new TypeReference<>() {});
            Map<String, List<ClubRecord>> clubsByCountry = Map.of(
                "ESP", read("data/initial/clubs/spain.json", new TypeReference<>() {}),
                "ARG", read("data/initial/clubs/argentina.json", new TypeReference<>() {}),
                "BRA", read("data/initial/clubs/brazil.json", new TypeReference<>() {})
            );

            validateInput(countries, leagues, clubsByCountry, specialAttributes);
            log.info("Three-league import: validated resources countries={} leagues={} clubs={}", countries.size(), leagues.size(), clubsByCountry.values().stream().mapToInt(List::size).sum());
            Map<String, SpecialAttributeRecord> specialAttributesByCode = specialAttributes.stream()
                .collect(LinkedHashMap::new, (map, attribute) -> map.put(attribute.code(), attribute), Map::putAll);
            upsertSystemUser();
            log.info("Three-league import: system owner ready");
            Map<String, UUID> countryIds = upsertCountries(countries);
            log.info("Three-league import: countries ready");
            Map<String, UUID> leagueIds = upsertLeagues(leagues, countryIds);
            log.info("Three-league import: leagues ready");
            Map<String, UUID> divisionIds = upsertDivisions(leagues, leagueIds);
            log.info("Three-league import: divisions ready");
            Map<String, UUID> specialAttributeIds = upsertSpecialAttributes(specialAttributes);
            log.info("Three-league import: special attributes ready");
            int clubs = 0;
            int teams = 0;
            int players = 0;
            int traitRows = 0;
            Set<String> playerExternalIds = new HashSet<>();

            for (LeagueRecord league : leagues) {
                List<ClubRecord> leagueClubs = clubsByCountry.get(league.countryCode());
                int sort = 1;
                UUID seasonExternalId = deterministicUuid("season:" + league.code() + ":" + league.seasonYear());
                log.info("Three-league import: season start league={}", league.code());
                int seasonId = upsertSeason(leagueIds.get(league.code()), seasonExternalId, league.seasonYear());
                log.info("Three-league import: season ready league={}", league.code());
                upsertSeasonCompetition(seasonId, leagueIds.get(league.code()), divisionIds.get(league.code()), league.name());

                for (ClubRecord club : leagueClubs) {
                    UUID clubId = deterministicUuid("club:" + league.countryCode() + ":" + club.code());
                    UUID teamId = deterministicUuid("team:" + league.countryCode() + ":" + club.code());
                    upsertClub(clubId, countryIds.get(league.countryCode()), club);
                    upsertTeam(teamId, clubId, leagueIds.get(league.code()), club);
                    upsertMembership(clubId, seasonId, leagueIds.get(league.code()), divisionIds.get(league.code()), sort++);
                    clubs++;
                    teams++;

                    List<PlayerRecord> squad = readExplicitSquad(league, club, teamId);
                    validateSquad(league, club, squad, specialAttributesByCode);
                    log.info("Three-league import: club start league={} club={} squad={}", league.code(), club.code(), squad.size());
                    clearTeamSquadOwnership(squad);
                    for (PlayerRecord player : squad) {
                        if (!playerExternalIds.add(player.externalId())) {
                            throw new IllegalArgumentException("Duplicate player externalId: " + player.externalId());
                        }
                        players++;
                        traitRows += 2;
                    }
                    upsertPlayers(squad, countryIds);
                    upsertTeamSquad(teamId, squad);
                    upsertSecondaryPositions(squad);
                    upsertPlayerSpecialAttributes(squad, specialAttributeIds);
                    log.info("Three-league import: club ready league={} club={}", league.code(), club.code());
                }
            }

            validateGlobal();
            log.info("Three-league import: global validation ready");
            return new ThreeLeagueImportReport(
                countries.size(), leagues.size(), clubs, teams, players, traitRows,
                List.of("Player identities are explicit public-identity records with MANAGER-estimated attributes."));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read MVP 1 dataset resources", e);
        }
    }

    public void validateGlobal() {
        requireZero("""
            SELECT COUNT(*) FROM (
                SELECT p.id
                FROM players p
                LEFT JOIN player_special_attributes psa ON psa.player_id = p.id
                WHERE p.source_system = ?
                GROUP BY p.id
                HAVING COUNT(psa.id) <> 2
            ) invalid
            """, "players without exactly two special attributes", SOURCE);
        requireZero("""
            SELECT COUNT(*) FROM player_special_attributes psa
            LEFT JOIN players p ON p.id = psa.player_id
            LEFT JOIN special_attributes sa ON sa.id = psa.special_attribute_id
            WHERE p.id IS NULL OR sa.id IS NULL
            """, "orphan special attribute rows");
        requireZero("""
            SELECT COUNT(*) FROM players
            WHERE source_system = ? AND (
                attack IS NULL OR defense IS NULL OR technique IS NULL OR speed IS NULL OR stamina IS NULL OR mentality IS NULL
                OR height_cm IS NULL OR height_cm NOT BETWEEN 160 AND 210
            )
            """, "invalid generated player attributes", SOURCE);
        requireZero("""
            SELECT COUNT(*) FROM players
            WHERE source_system = ? AND (
                source_entity_id IS NULL OR source_entity_id = ''
                OR identity_source_ref IS NULL OR identity_source_ref = ''
                OR position_source_ref IS NULL OR position_source_ref = ''
            )
            """, "players without required source references", SOURCE);
        requireZero("""
            SELECT COUNT(*) FROM players
            WHERE source_system = ?
              AND position NOT IN ('GK','CB','LB','RB','LWB','RWB','CDM','CM','CAM','LM','RM','LW','RW','ST','CF')
            """, "players with invalid primary position", SOURCE);
    }

    private <T> T read(String path, TypeReference<T> type) throws IOException {
        try (var in = new ClassPathResource(path).getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (!json.isEmpty() && json.charAt(0) == '\uFEFF') {
                json = json.substring(1);
            }
            return objectMapper.readValue(json, type);
        }
    }

    private void validateInput(List<CountryRecord> countries, List<LeagueRecord> leagues,
                               Map<String, List<ClubRecord>> clubsByCountry,
                               List<SpecialAttributeRecord> specialAttributes) {
        requireUnique(countries.stream().map(CountryRecord::code).toList(), "country code");
        requireUnique(leagues.stream().map(LeagueRecord::code).toList(), "league code");
        requireUnique(specialAttributes.stream().map(SpecialAttributeRecord::code).toList(), "special attribute code");
        Set<String> countryCodes = new HashSet<>(countries.stream().map(CountryRecord::code).toList());
        for (LeagueRecord league : leagues) {
            if (!countryCodes.contains(league.countryCode())) {
                throw new IllegalArgumentException("League references unknown country: " + league.code());
            }
            List<ClubRecord> clubs = clubsByCountry.getOrDefault(league.countryCode(), List.of());
            if (clubs.size() != league.teamCount()) {
                throw new IllegalArgumentException(
                    league.code() + " expected " + league.teamCount() + " clubs, got " + clubs.size());
            }
            requireUnique(clubs.stream().map(ClubRecord::code).toList(), "club code for " + league.code());
        }
    }

    private void validateSquad(LeagueRecord league, ClubRecord club, List<PlayerRecord> squad,
                               Map<String, SpecialAttributeRecord> specialAttributesByCode) {
        if (squad.size() != PLAYERS_PER_CLUB) {
            throw new IllegalArgumentException(club.code() + " invalid squad size: " + squad.size());
        }
        Map<String, Long> byPosition = new HashMap<>();
        for (PlayerRecord player : squad) {
            validatePlayerIdentity(league, club, player);
            byPosition.merge(positionGroup(player.primaryPosition()), 1L, Long::sum);
            if (player.heightCm() < 160 || player.heightCm() > 210) {
                throw new IllegalArgumentException("Invalid height for " + player.fullName());
            }
            if (List.of(player.attack(), player.defense(), player.technique(), player.speed(), player.stamina(), player.mentality())
                .stream().anyMatch(v -> v < 1 || v > 99)) {
                throw new IllegalArgumentException("Invalid attribute range for " + player.fullName());
            }
            var selection = specialAttributeSelectionValidator.validate(
                player.specialAttributes(),
                specialAttributesByCode.keySet());
            for (String code : selection.codes()) {
                SpecialAttributeRecord attribute = specialAttributesByCode.get(code);
                String group = positionGroup(player.primaryPosition());
                if (attribute == null || (!attribute.positions().isEmpty()
                    && !attribute.positions().contains(group))) {
                    throw new IllegalArgumentException(
                        "Special attribute " + code + " is not compatible with " + player.primaryPosition()
                            + " for " + player.fullName());
                }
            }
        }
        if (byPosition.getOrDefault("GK", 0L) < 1) {
            throw new IllegalArgumentException("Squad has no goalkeeper for " + league.code() + "/" + club.code());
        }
        long outfieldPlayers = squad.size() - byPosition.getOrDefault("GK", 0L);
        if (outfieldPlayers < 10) {
            throw new IllegalArgumentException("Squad has fewer than ten outfield players for " + league.code() + "/" + club.code());
        }
    }

    private void validatePlayerIdentity(LeagueRecord league, ClubRecord club, PlayerRecord player) {
        String context = league.code() + "/" + club.code() + "/" + player.fullName();
        requireCleanText(player.fullName(), "fullName", context);
        requireCleanText(player.displayName(), "displayName", context);
        if (player.externalId() == null || !player.externalId().startsWith("public-player:")) {
            throw new IllegalArgumentException("Invalid transfer-stable externalId for " + context);
        }
        if (player.externalId().contains(":" + club.code() + ":")
            || player.externalId().startsWith("public-identity:")) {
            throw new IllegalArgumentException("Club-dependent externalId for " + context + ": " + player.externalId());
        }
        requireCleanText(player.identitySourceName(), "identitySourceName", context);
        requireConcreteSource(player.identitySourceRef(), "identitySourceRef", context);
        requireConcreteSource(player.positionSourceRef(), "positionSourceRef", context);
        if (player.positionCheckedAt() == null || player.positionCheckedAt().isBlank()) {
            throw new IllegalArgumentException("Missing positionCheckedAt for " + context);
        }
        positionGroup(player.primaryPosition());
    }

    private static void requireCleanText(String value, String field, String context) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + field + " for " + context);
        }
        if (value.contains("\uFFFD") || value.contains("?") || value.contains("Ã")
            || value.contains("â€") || value.contains("â†") || value.contains("â")) {
            throw new IllegalArgumentException("Corrupt text in " + field + " for " + context + ": " + value);
        }
    }

    private static void requireConcreteSource(String value, String field, String context) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + field + " for " + context);
        }
        if (!value.startsWith("http://") && !value.startsWith("https://") && !value.startsWith("docs/")) {
            throw new IllegalArgumentException("Non-reconstructable " + field + " for " + context + ": " + value);
        }
    }

    private void upsertSystemUser() {
        jdbcTemplate.update("""
            INSERT INTO users (id, email, username, password_hash, role)
            VALUES (?, 'mvp1-import@manager.local', 'mvp1-import', 'not-used', 'ADMIN')
            ON CONFLICT (id) DO NOTHING
            """, SYSTEM_USER_ID);
    }

    private Map<String, UUID> upsertCountries(List<CountryRecord> countries) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (CountryRecord country : countries) {
            UUID id = deterministicUuid("country:" + country.code());
            jdbcTemplate.update("""
                INSERT INTO countries (id, code, name, demonym, confederation)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET
                    name = EXCLUDED.name, demonym = EXCLUDED.demonym,
                    confederation = EXCLUDED.confederation, updated_at = NOW()
                """, id, country.code(), country.name(), country.demonym(), country.confederation());
            ids.put(country.code(), id);
        }
        return ids;
    }

    private Map<String, UUID> upsertLeagues(List<LeagueRecord> leagues, Map<String, UUID> countryIds) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (LeagueRecord league : leagues) {
            UUID id = deterministicUuid("league:" + league.code());
            jdbcTemplate.update("""
                INSERT INTO leagues (id, country_id, code, name, country, tier, team_count, rules_json, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                ON CONFLICT (code) DO UPDATE SET
                    country_id = EXCLUDED.country_id, name = EXCLUDED.name, country = EXCLUDED.country,
                    tier = EXCLUDED.tier, team_count = EXCLUDED.team_count,
                    rules_json = EXCLUDED.rules_json, updated_at = NOW()
                """, id, countryIds.get(league.countryCode()), league.code(), league.name(), league.countryCode(),
                league.tier(), league.teamCount(), "{\"format\":\"" + league.format() + "\"}");
            ids.put(league.code(), id);
        }
        return ids;
    }

    private Map<String, UUID> upsertDivisions(List<LeagueRecord> leagues, Map<String, UUID> leagueIds) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (LeagueRecord league : leagues) {
            UUID id = deterministicUuid("division:" + league.code() + ":1");
            jdbcTemplate.update("""
                INSERT INTO divisions (id, league_id, code, name, tier, team_count, sort_order)
                VALUES (?, ?, ?, ?, 1, ?, 1)
                ON CONFLICT (league_id, code) DO UPDATE SET
                    name = EXCLUDED.name, team_count = EXCLUDED.team_count, updated_at = NOW()
                """, id, leagueIds.get(league.code()), league.code() + "-D1", league.name(), league.teamCount());
            ids.put(league.code(), id);
        }
        return ids;
    }

    private int upsertSeason(UUID leagueId, UUID externalId, int seasonYear) {
        jdbcTemplate.update("""
            INSERT INTO seasons (external_id, season_year, league_id, status, starts_at, ends_at)
            VALUES (?, ?, ?, 'READY', ?, ?)
            ON CONFLICT (external_id) DO UPDATE SET
                season_year = EXCLUDED.season_year, league_id = EXCLUDED.league_id,
                status = EXCLUDED.status, starts_at = EXCLUDED.starts_at,
                ends_at = EXCLUDED.ends_at, updated_at = NOW()
            """, externalId, seasonYear, leagueId, LocalDate.of(seasonYear, 1, 1), LocalDate.of(seasonYear, 12, 31));
        return jdbcTemplate.queryForObject("SELECT id FROM seasons WHERE external_id = ?", Integer.class, externalId);
    }

    private void upsertSeasonCompetition(int seasonId, UUID leagueId, UUID divisionId, String name) {
        jdbcTemplate.update("""
            INSERT INTO season_competitions (season_id, league_id, division_id, name, status)
            VALUES (?, ?, ?, ?, 'READY')
            ON CONFLICT (season_id, league_id, division_id) DO UPDATE SET name = EXCLUDED.name, status = EXCLUDED.status
            """, seasonId, leagueId, divisionId, name);
    }

    private void upsertClub(UUID clubId, UUID countryId, ClubRecord club) {
        jdbcTemplate.update("""
            INSERT INTO clubs (id, source_system, source_id, country_id, name, short_name, reputation, budget)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                source_system = EXCLUDED.source_system, source_id = EXCLUDED.source_id,
                country_id = EXCLUDED.country_id, name = EXCLUDED.name, short_name = EXCLUDED.short_name,
                reputation = EXCLUDED.reputation, budget = EXCLUDED.budget, updated_at = NOW()
            """, clubId, SOURCE, club.code(), countryId, club.name(), club.shortName(), club.reputation(),
            BigDecimal.valueOf(club.reputation()).multiply(BigDecimal.valueOf(1_000_000L)));
    }

    private void upsertTeam(UUID teamId, UUID clubId, UUID leagueId, ClubRecord club) {
        jdbcTemplate.update("""
            INSERT INTO teams (id, club_id, manager_id, league_id, name, country, budget, formation, division)
            VALUES (?, ?, ?, ?, ?, ?, ?, '4-3-3', 'PRIMERA')
            ON CONFLICT (id) DO UPDATE SET
                club_id = EXCLUDED.club_id, league_id = EXCLUDED.league_id, name = EXCLUDED.name,
                budget = EXCLUDED.budget, updated_at = NOW()
            """, teamId, clubId, SYSTEM_USER_ID, leagueId, club.name(), "", BigDecimal.valueOf(club.reputation()).multiply(BigDecimal.valueOf(1_000_000L)));
        jdbcTemplate.update("""
            INSERT INTO league_teams (league_id, team_id) VALUES (?, ?)
            ON CONFLICT (league_id, team_id) DO NOTHING
            """, leagueId, teamId);
    }

    private void upsertMembership(UUID clubId, int seasonId, UUID leagueId, UUID divisionId, int sortOrder) {
        jdbcTemplate.update("""
            INSERT INTO club_division_memberships (club_id, season_id, league_id, division_id, sort_order)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (club_id, season_id) DO UPDATE SET
                league_id = EXCLUDED.league_id, division_id = EXCLUDED.division_id, sort_order = EXCLUDED.sort_order
            """, clubId, seasonId, leagueId, divisionId, sortOrder);
    }

    private Map<String, UUID> upsertSpecialAttributes(List<SpecialAttributeRecord> attributes) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (SpecialAttributeRecord attribute : attributes) {
            UUID id = deterministicUuid("special-attribute:" + attribute.code());
            jdbcTemplate.update("""
                INSERT INTO special_attributes (id, code, name, description)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description
                """, id, attribute.code(), attribute.name(), attribute.description());
            UUID storedId = jdbcTemplate.queryForObject(
                "SELECT id FROM special_attributes WHERE code = ?",
                UUID.class,
                attribute.code());
            ids.put(attribute.code(), storedId);
        }
        return ids;
    }

    private List<PlayerRecord> readExplicitSquad(LeagueRecord league, ClubRecord club, UUID teamId) throws IOException {
        String countryPath = switch (league.countryCode()) {
            case "ESP" -> "spain";
            case "ARG" -> "argentina";
            case "BRA" -> "brazil";
            default -> throw new IllegalArgumentException("Unsupported country for explicit squad: " + league.countryCode());
        };
        List<PlayerSourceRecord> sourcePlayers = read(
            "data/initial/players/" + countryPath + "/" + club.code() + ".json",
            new TypeReference<>() {});
        List<PlayerRecord> players = new ArrayList<>();
        for (PlayerSourceRecord source : sourcePlayers) {
            if (!club.code().equals(source.clubExternalId())) {
                throw new IllegalArgumentException(
                    source.externalId() + " references unexpected club " + source.clubExternalId());
            }
            validatePlayerSource(league, club, source);
            PlayerAttributesRecord attributes = source.attributes();
            players.add(new PlayerRecord(
                deterministicUuid("player:" + source.externalId()),
                teamId,
                SOURCE,
                source.externalId(),
                source.fullName(),
                source.displayName(),
                LocalDate.parse(source.dateOfBirth()),
                source.nationalityCode(),
                source.primaryPosition(),
                source.secondaryPositions() == null ? List.of() : source.secondaryPositions(),
                source.preferredFoot(),
                source.shirtNumber() == null ? 0 : source.shirtNumber(),
                source.heightCm(),
                attributes.attack(),
                attributes.defense(),
                attributes.technique(),
                attributes.speed(),
                attributes.stamina(),
                attributes.mentality(),
                source.marketValue(),
                source.specialAttributes(),
                source.sourceEntityId(),
                source.identitySourceName(),
                source.identitySourceRef(),
                source.identityCheckedAt(),
                source.positionSourceRef(),
                source.positionCheckedAt(),
                source.positionEstimated()));
        }
        return players;
    }

    private void validatePlayerSource(LeagueRecord league, ClubRecord club, PlayerSourceRecord source) {
        String context = league.code() + "/" + club.code() + "/" + source.fullName();
        requireCleanText(source.fullName(), "fullName", context);
        requireCleanText(source.displayName(), "displayName", context);
        requireConcreteSource(source.identitySourceRef(), "identitySourceRef", context);
        requireConcreteSource(source.positionSourceRef(), "positionSourceRef", context);
        if (source.externalId() == null || !source.externalId().startsWith("public-player:")) {
            throw new IllegalArgumentException("Invalid transfer-stable externalId for " + context);
        }
        if (source.externalId().startsWith("public-identity:")
            || source.externalId().contains(":" + club.code() + ":")) {
            throw new IllegalArgumentException("Club-dependent externalId for " + context + ": " + source.externalId());
        }
        positionGroup(source.primaryPosition());
    }

    private void upsertPlayers(List<PlayerRecord> players, Map<String, UUID> countryIds) {
        jdbcTemplate.batchUpdate("""
            INSERT INTO players (
                id, source_system, source_id, source_entity_id, identity_source_name, identity_source_ref,
                identity_checked_at, position_source_ref, position_checked_at, position_estimated,
                country_id, name, display_name, age, birth_date, position,
                dominant_foot, shirt_number, attack, defense, technique, speed, stamina, mentality,
                market_value, weekly_salary, energy, injured, height_cm, skill_levels_json, contract_status
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 100, FALSE, ?, ?, 'ACTIVE')
            ON CONFLICT (source_system, source_id) DO UPDATE SET
                source_entity_id = EXCLUDED.source_entity_id,
                identity_source_name = EXCLUDED.identity_source_name,
                identity_source_ref = EXCLUDED.identity_source_ref,
                identity_checked_at = EXCLUDED.identity_checked_at,
                position_source_ref = EXCLUDED.position_source_ref,
                position_checked_at = EXCLUDED.position_checked_at,
                position_estimated = EXCLUDED.position_estimated,
                country_id = EXCLUDED.country_id, name = EXCLUDED.name, display_name = EXCLUDED.display_name,
                age = EXCLUDED.age, birth_date = EXCLUDED.birth_date, position = EXCLUDED.position,
                dominant_foot = EXCLUDED.dominant_foot, shirt_number = EXCLUDED.shirt_number,
                attack = EXCLUDED.attack, defense = EXCLUDED.defense, technique = EXCLUDED.technique,
                speed = EXCLUDED.speed, stamina = EXCLUDED.stamina, mentality = EXCLUDED.mentality,
                market_value = EXCLUDED.market_value, weekly_salary = EXCLUDED.weekly_salary,
                height_cm = EXCLUDED.height_cm, skill_levels_json = EXCLUDED.skill_levels_json,
                updated_at = NOW()
            """, players.stream().map(player -> {
                int age = LocalDate.now().getYear() - player.birthDate().getYear();
                String skillsJson = "{\"PASSER\":" + player.technique()
                    + ",\"SHOOTER\":" + player.attack()
                    + ",\"TACKLER\":" + player.defense()
                    + ",\"SPEEDSTER\":" + player.speed() + "}";
                return new Object[] {player.id(), player.sourceSystem(), player.externalId(), player.sourceEntityId(),
                    player.identitySourceName(), player.identitySourceRef(), LocalDate.parse(player.identityCheckedAt()),
                    player.positionSourceRef(), LocalDate.parse(player.positionCheckedAt()),
                    Boolean.TRUE.equals(player.positionEstimated()), countryIds.get(player.nationalityCode()), player.fullName(),
                    player.displayName(), age, player.birthDate(), player.primaryPosition(), player.preferredFoot(),
                    player.shirtNumber(), player.attack(), player.defense(), player.technique(), player.speed(),
                    player.stamina(), player.mentality(), player.marketValue(), player.marketValue().divide(BigDecimal.valueOf(250)),
                    player.heightCm(), skillsJson};
            }).toList());
    }

    private void clearTeamSquadOwnership(List<PlayerRecord> squad) {
        if (squad.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
            "DELETE FROM team_squad WHERE player_id = ?",
            squad.stream().map(PlayerRecord::id).map(id -> new Object[] {id}).toList());
    }

    private void upsertTeamSquad(UUID teamId, List<PlayerRecord> squad) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO team_squad (team_id, player_id) VALUES (?, ?) ON CONFLICT (team_id, player_id) DO NOTHING",
            squad.stream().map(player -> new Object[] {teamId, player.id()}).toList());
    }

    private void upsertSecondaryPositions(List<PlayerRecord> players) {
        jdbcTemplate.batchUpdate("DELETE FROM player_secondary_positions WHERE player_id = ?",
            players.stream().map(player -> new Object[] {player.id()}).toList());
        List<Object[]> rows = players.stream()
            .flatMap(player -> player.secondaryPositions().stream().map(position -> new Object[] {player.id(), position}))
            .toList();
        if (!rows.isEmpty()) {
            jdbcTemplate.batchUpdate("""
                INSERT INTO player_secondary_positions (player_id, position)
                VALUES (?, ?) ON CONFLICT (player_id, position) DO NOTHING
                """, rows);
        }
    }

    private void upsertPlayerSpecialAttributes(List<PlayerRecord> players, Map<String, UUID> specialAttributeIds) {
        jdbcTemplate.batchUpdate("DELETE FROM player_special_attributes WHERE player_id = ?",
            players.stream().map(player -> new Object[] {player.id()}).toList());
        List<Object[]> rows = new ArrayList<>();
        for (PlayerRecord player : players) {
            var selection = specialAttributeSelectionValidator.validate(player.specialAttributes(), specialAttributeIds.keySet());
            int slot = 1;
            for (String code : selection.codes()) {
                rows.add(new Object[] {player.id(), specialAttributeIds.get(code), slot++});
            }
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO player_special_attributes (player_id, special_attribute_id, slot)
                VALUES (?, ?, ?)
                """, rows);
    }

    private void requireZero(String sql, String label, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        if (count != null && count > 0) {
            throw new IllegalStateException("Global validation failed for " + label + ": " + count);
        }
    }

    private static void requireUnique(List<String> values, String label) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || !seen.add(value)) {
                throw new IllegalArgumentException("Duplicate or blank " + label + ": " + value);
            }
        }
    }

    private static UUID deterministicUuid(String key) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            bytes[6] &= 0x0f;
            bytes[6] |= 0x40;
            bytes[8] &= 0x3f;
            bytes[8] |= (byte) 0x80;
            return UUID.nameUUIDFromBytes(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build deterministic UUID", e);
        }
    }

    private static String positionGroup(String position) {
        return switch (position) {
            case "GK" -> "GK";
            case "LB", "CB", "RB", "LWB", "RWB" -> "DEF";
            case "CDM", "CM", "CAM", "LM", "RM" -> "MID";
            case "LW", "RW" -> "WINGER";
            case "CF", "ST" -> "ATT";
            default -> throw new IllegalArgumentException("Unsupported player position: " + position);
        };
    }

    public record CountryRecord(String code, String name, String demonym, String confederation) {}
    public record LeagueRecord(String code, String name, String countryCode, int tier, int teamCount, int seasonYear, String format) {}
    public record ClubRecord(String code, String name, String shortName, String city, int reputation) {}
    public record SpecialAttributeRecord(String code, String name, String category, List<String> positions, String description, String futureEffect) {}
    public record PlayerSourceRecord(
        String externalId, String fullName, String displayName, String dateOfBirth, String nationalityCode,
        String clubExternalId, String primaryPosition, List<String> secondaryPositions, String preferredFoot,
        Integer heightCm, Integer shirtNumber, PlayerAttributesRecord attributes, BigDecimal marketValue,
        List<String> specialAttributes, String identitySource, String identityCheckedAt,
        String identitySourceName, String identitySourceRef, String sourceEntityId,
        String positionSource, String positionSourceRef, String positionCheckedAt, Boolean positionEstimated,
        Map<String, Object> provenance, List<String> estimatedFields
    ) {}
    public record PlayerAttributesRecord(int attack, int defense, int technique, int speed, int stamina, int mentality) {}
    private record PlayerRecord(
        UUID id, UUID teamId, String sourceSystem, String externalId, String fullName, String displayName,
        LocalDate birthDate, String nationalityCode, String primaryPosition, List<String> secondaryPositions,
        String preferredFoot, int shirtNumber, int heightCm, int attack, int defense, int technique,
        int speed, int stamina, int mentality, BigDecimal marketValue, List<String> specialAttributes,
        String sourceEntityId, String identitySourceName, String identitySourceRef, String identityCheckedAt,
        String positionSourceRef,
        String positionCheckedAt, Boolean positionEstimated
    ) {}
}
