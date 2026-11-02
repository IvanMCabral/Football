package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.footballmanager.domain.model.entity.WorldSnapshot;
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
            // The final canonicalization orders every object and array. Doing
            // the same normalization per entity first was redundant: C(C(x)) = C(x).
            root.set("leagues", objectMapper.valueToTree(catalog.getLeagues() == null ? List.of() : catalog.getLeagues()));
            root.set("teams", objectMapper.valueToTree(values(catalog.getWorldTeams())));
            root.set("players", objectMapper.valueToTree(values(catalog.getWorldPlayers())));
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

    private static <T> Iterable<T> values(Map<String, T> map) {
        return map == null ? List.of() : map.values();
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
        // Keep the exact lexical ordering, but serialize each element once
        // instead of once for every comparison of a large player array.
        record Element(JsonNode value, String key) {}
        List<Element> elements = new ArrayList<>();
        node.forEach(value -> {
            JsonNode canonical = canonicalize(value);
            elements.add(new Element(canonical, canonical.toString()));
        });
        elements.sort(Comparator.comparing(Element::key));
        elements.forEach(element -> sorted.add(element.value()));
        return sorted;
    }
}
