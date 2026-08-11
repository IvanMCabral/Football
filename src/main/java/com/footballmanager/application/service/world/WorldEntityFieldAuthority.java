package com.footballmanager.application.service.world;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fail-closed classification of every persisted World model field. */
@Component
public final class WorldEntityFieldAuthority {

    public enum Authority { CANONICAL, OVERLAY_DELTA, EPHEMERAL_IGNORED }

    public enum SerializationAuthority {
        PERSISTED_MATERIAL,
        DERIVED_VIEW_IGNORED
    }

    private final Map<Class<?>, Map<String, Authority>> fields = build();
    private final Map<Class<?>, Map<String, SerializationAuthority>> jacksonProperties = buildJacksonProperties();
    private final ObjectMapper objectMapper;

    public WorldEntityFieldAuthority() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    public WorldEntityFieldAuthority(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
    }

    public Map<Class<?>, Map<String, Authority>> classifications() {
        Map<Class<?>, Map<String, Authority>> copy = new LinkedHashMap<>();
        fields.forEach((type, value) -> copy.put(type, Map.copyOf(value)));
        return Map.copyOf(copy);
    }

    public List<String> uncoveredFields() {
        List<String> uncovered = new ArrayList<>();
        fields.forEach((type, classified) -> {
            Set<String> actual = instanceFields(type);
            actual.stream().filter(name -> !classified.containsKey(name))
                    .forEach(name -> uncovered.add(type.getSimpleName() + "." + name));
            classified.keySet().stream().filter(name -> !actual.contains(name))
                    .forEach(name -> uncovered.add(type.getSimpleName() + "." + name + ":stale-classification"));
        });
        return List.copyOf(uncovered);
    }

    public Map<Class<?>, Map<String, SerializationAuthority>> jacksonClassifications() {
        Map<Class<?>, Map<String, SerializationAuthority>> copy = new LinkedHashMap<>();
        jacksonProperties.forEach((type, value) -> copy.put(type, Map.copyOf(value)));
        return Map.copyOf(copy);
    }

    public List<String> uncoveredJacksonProperties() {
        List<String> uncovered = new ArrayList<>();
        jacksonProperties.forEach((type, classified) -> {
            Set<String> actual = jacksonVisibleProperties(type);
            actual.stream().filter(name -> !classified.containsKey(name))
                    .forEach(name -> uncovered.add(type.getSimpleName() + "." + name));
            classified.keySet().stream().filter(name -> !actual.contains(name))
                    .forEach(name -> uncovered.add(type.getSimpleName() + "." + name + ":stale-classification"));
        });
        return List.copyOf(uncovered);
    }

    /** Testable fail-closed primitive used by the authority itself and destructive controls. */
    public List<String> uncoveredJacksonProperties(Class<?> type, Set<String> classifiedProperties) {
        Set<String> actual = jacksonVisibleProperties(type);
        List<String> uncovered = new ArrayList<>();
        actual.stream().filter(name -> !classifiedProperties.contains(name)).sorted()
                .forEach(name -> uncovered.add(type.getSimpleName() + "." + name));
        classifiedProperties.stream().filter(name -> !actual.contains(name)).sorted()
                .forEach(name -> uncovered.add(type.getSimpleName() + "." + name + ":stale-classification"));
        return List.copyOf(uncovered);
    }

    public void requireComplete() {
        List<String> uncovered = uncoveredFields();
        uncovered = new ArrayList<>(uncovered);
        uncovered.addAll(uncoveredJacksonProperties());
        if (!uncovered.isEmpty()) {
            throw new IllegalStateException("Unclassified World serialization contract: "
                    + String.join(", ", uncovered));
        }
    }

    private Set<String> jacksonVisibleProperties(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        objectMapper.getSerializationConfig().introspect(objectMapper.constructType(type))
                .findProperties().stream().map(BeanPropertyDefinition::getName).forEach(names::add);
        objectMapper.getDeserializationConfig().introspect(objectMapper.constructType(type))
                .findProperties().stream().map(BeanPropertyDefinition::getName).forEach(names::add);
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && method.isAnnotationPresent(JsonIgnore.class)) {
                String name = beanPropertyName(method.getName());
                if (name != null) names.add(name);
            }
        }
        return names;
    }

    private static String beanPropertyName(String method) {
        String suffix = method.startsWith("get") && method.length() > 3 ? method.substring(3)
                : method.startsWith("is") && method.length() > 2 ? method.substring(2) : null;
        if (suffix == null) return null;
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private static Set<String> instanceFields(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) names.add(field.getName());
        }
        return names;
    }

    private static Map<Class<?>, Map<String, Authority>> build() {
        Map<Class<?>, Map<String, Authority>> result = new LinkedHashMap<>();
        result.put(WorldTeam.class, map(
                "worldTeamId", Authority.CANONICAL,
                "realTeamId", Authority.CANONICAL,
                "realLeagueId", Authority.OVERLAY_DELTA,
                "name", Authority.OVERLAY_DELTA,
                "country", Authority.OVERLAY_DELTA,
                "city", Authority.OVERLAY_DELTA,
                "baseBudget", Authority.OVERLAY_DELTA,
                "baseFormation", Authority.OVERLAY_DELTA,
                "origin", Authority.CANONICAL,
                "division", Authority.OVERLAY_DELTA));
        result.put(WorldPlayer.class, map(
                "worldPlayerId", Authority.CANONICAL,
                "realPlayerId", Authority.CANONICAL,
                "worldTeamId", Authority.OVERLAY_DELTA,
                "name", Authority.OVERLAY_DELTA,
                "age", Authority.OVERLAY_DELTA,
                "position", Authority.OVERLAY_DELTA,
                "baseAttack", Authority.OVERLAY_DELTA,
                "baseDefense", Authority.OVERLAY_DELTA,
                "baseTechnique", Authority.OVERLAY_DELTA,
                "baseSpeed", Authority.OVERLAY_DELTA,
                "baseStamina", Authority.OVERLAY_DELTA,
                "baseMentality", Authority.OVERLAY_DELTA,
                "baseMarketValue", Authority.OVERLAY_DELTA,
                "origin", Authority.CANONICAL,
                "heightCm", Authority.OVERLAY_DELTA,
                "skillLevels", Authority.OVERLAY_DELTA,
                "specialTraits", Authority.OVERLAY_DELTA));
        result.put(WorldLeague.class, map(
                "realLeagueId", Authority.CANONICAL,
                "name", Authority.OVERLAY_DELTA,
                "country", Authority.OVERLAY_DELTA,
                "tier", Authority.OVERLAY_DELTA));
        result.put(WorldSnapshot.class, map(
                "userId", Authority.OVERLAY_DELTA,
                "leagues", Authority.OVERLAY_DELTA,
                "worldTeams", Authority.OVERLAY_DELTA,
                "worldPlayers", Authority.OVERLAY_DELTA,
                "worldPlayerAliases", Authority.OVERLAY_DELTA,
                "createdAt", Authority.OVERLAY_DELTA,
                "lastUpdated", Authority.OVERLAY_DELTA));
        return result;
    }

    private static Map<Class<?>, Map<String, SerializationAuthority>> buildJacksonProperties() {
        Map<Class<?>, Map<String, SerializationAuthority>> result = new LinkedHashMap<>();
        result.put(WorldTeam.class, persisted(
                "worldTeamId", "realTeamId", "realLeagueId", "name", "country", "city",
                "baseBudget", "baseFormation", "origin", "division"));
        result.put(WorldPlayer.class, persisted(
                "worldPlayerId", "realPlayerId", "worldTeamId", "name", "age", "position",
                "baseAttack", "baseDefense", "baseTechnique", "baseSpeed", "baseStamina",
                "baseMentality", "baseMarketValue", "origin", "heightCm", "skillLevels",
                "specialTraits"));
        result.put(WorldLeague.class, persisted("realLeagueId", "name", "country", "tier"));
        Map<String, SerializationAuthority> snapshot = persisted(
                "userId", "leagues", "worldTeams", "worldPlayers", "worldPlayerAliases",
                "createdAt", "lastUpdated");
        snapshot.put("allWorldTeams", SerializationAuthority.DERIVED_VIEW_IGNORED);
        snapshot.put("allWorldPlayers", SerializationAuthority.DERIVED_VIEW_IGNORED);
        result.put(WorldSnapshot.class, snapshot);
        return result;
    }

    private static Map<String, SerializationAuthority> persisted(String... names) {
        Map<String, SerializationAuthority> result = new LinkedHashMap<>();
        for (String name : names) result.put(name, SerializationAuthority.PERSISTED_MATERIAL);
        return result;
    }

    private static Map<String, Authority> map(Object... entries) {
        Map<String, Authority> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put((String) entries[i], (Authority) entries[i + 1]);
        }
        return result;
    }
}
