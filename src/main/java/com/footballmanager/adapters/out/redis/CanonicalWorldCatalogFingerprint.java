package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Domain-aware, ordering-independent identity for a shared world catalog. */
@Component
public final class CanonicalWorldCatalogFingerprint {

    public static final int SCHEMA_VERSION = 2;
    public static final String MATERIAL_CONFIGURATION = "canonical-world-content-v2";

    private final ObjectMapper objectMapper;

    public CanonicalWorldCatalogFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String fingerprint(WorldSnapshot catalog) {
        return fingerprint(catalog, SCHEMA_VERSION, MATERIAL_CONFIGURATION);
    }

    String fingerprint(WorldSnapshot catalog, int schemaVersion, String materialConfiguration) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", schemaVersion);
            root.put("materialConfiguration", materialConfiguration);
            root.set("leagues", sorted(catalog.getLeagues(), this::leagueKey));
            root.set("teams", sorted(values(catalog.getWorldTeams()), this::teamKey));
            root.set("players", sorted(values(catalog.getWorldPlayers()), this::playerKey));
            byte[] material = objectMapper.writeValueAsBytes(canonicalize(root));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for world catalog identity", e);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot fingerprint canonical world catalog", e);
        }
    }

    private <T> ArrayNode sorted(Iterable<T> values, java.util.function.Function<T, String> stableKey) {
        List<T> ordered = new ArrayList<>();
        if (values != null) values.forEach(ordered::add);
        ordered.sort(Comparator.comparing(stableKey, Comparator.nullsFirst(String::compareTo))
                .thenComparing(value -> canonicalize(objectMapper.valueToTree(value)).toString()));
        ArrayNode result = objectMapper.createArrayNode();
        ordered.forEach(value -> result.add(canonicalize(objectMapper.valueToTree(value))));
        return result;
    }

    private static <T> Iterable<T> values(Map<String, T> map) {
        return map == null ? List.of() : map.values();
    }

    private String leagueKey(WorldLeague league) {
        return league == null || league.getRealLeagueId() == null
                ? "" : league.getRealLeagueId().toString();
    }

    private String teamKey(WorldTeam team) {
        if (team == null) return "";
        return team.getRealTeamId() == null ? String.valueOf(team.getWorldTeamId()) : team.getRealTeamId().toString();
    }

    private String playerKey(WorldPlayer player) {
        if (player == null) return "";
        return player.getRealPlayerId() == null ? String.valueOf(player.getWorldPlayerId()) : player.getRealPlayerId().toString();
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isValueNode()) return node;
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            iterator.forEachRemaining(entry -> fields.put(entry.getKey(), canonicalize(entry.getValue())));
            fields.forEach(sorted::set);
            return sorted;
        }
        ArrayNode sorted = objectMapper.createArrayNode();
        List<JsonNode> elements = new ArrayList<>();
        node.forEach(value -> elements.add(canonicalize(value)));
        elements.sort(Comparator.comparing(JsonNode::toString));
        elements.forEach(sorted::add);
        return sorted;
    }
}
