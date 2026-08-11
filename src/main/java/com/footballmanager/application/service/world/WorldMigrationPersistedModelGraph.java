package com.footballmanager.application.service.world;

import com.footballmanager.application.service.simulation.detailed.BaselineState;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.RuntimeMatch;
import com.footballmanager.domain.model.entity.Standing;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.valueobject.LineupSlot;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Structural inventory of every model reachable from the durable Redis roots. */
public final class WorldMigrationPersistedModelGraph {

    private static final Set<Class<?>> DEFAULT_ROOTS = Set.of(
            WorldSnapshot.class, CareerSave.class, RuntimeMatch.class, MatchState.class,
            MatchCommand.class, Standing.class, DetailedMatchData.class, BaselineState.class);

    private static final Map<String, Set<Class<?>>> COMPATIBILITY_OVERRIDES = Map.of(
            "CareerSave.teamStarting11Subdivision", Set.of(String.class, LineupSlot.class),
            "MatchCommand.payload", Set.of(String.class));

    public Graph discover() {
        return inspect(DEFAULT_ROOTS, COMPATIBILITY_OVERRIDES);
    }

    public Graph inspect(Set<Class<?>> roots, Map<String, Set<Class<?>>> overrides) {
        Set<Class<?>> models = new LinkedHashSet<>();
        Set<String> fields = new LinkedHashSet<>();
        Set<String> containers = new LinkedHashSet<>();
        Set<String> annotatedReferences = new LinkedHashSet<>();
        List<String> unresolved = new ArrayList<>();
        Deque<Class<?>> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            Class<?> type = pending.removeFirst();
            if (terminal(type) || !models.add(type)) continue;
            List<Field> declared = new ArrayList<>(List.of(type.getDeclaredFields()));
            declared.sort(Comparator.comparing(Field::getName));
            for (Field field : declared) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                        || Modifier.isTransient(field.getModifiers())) continue;
                String path = type.getSimpleName() + "." + field.getName();
                fields.add(path);
                WorldIdentityReference marker = field.getAnnotation(WorldIdentityReference.class);
                if (marker != null) {
                    for (WorldIdentityReference.Location location : marker.locations()) {
                        annotatedReferences.add(path + "@" + location + ":" + marker.role());
                    }
                }
                Set<Class<?>> override = overrides.get(path);
                if (override != null) {
                    containers.add(path + "<compatibility-union>");
                    override.stream().filter(candidate -> !terminal(candidate)).forEach(pending::addLast);
                    continue;
                }
                inspectType(field.getGenericType(), path, pending, containers, unresolved);
            }
        }
        return new Graph(Set.copyOf(roots), Set.copyOf(models), Set.copyOf(fields),
                Set.copyOf(containers), Set.copyOf(annotatedReferences), List.copyOf(unresolved));
    }

    private static void inspectType(Type type, String path, Deque<Class<?>> pending,
                                    Set<String> containers, List<String> unresolved) {
        if (type instanceof Class<?> raw) {
            if (raw.isArray()) {
                containers.add(path + "[]");
                inspectType(raw.getComponentType(), path + "[]", pending, containers, unresolved);
            } else if (raw == Object.class) {
                unresolved.add(path + ":raw-object");
            } else if (!terminal(raw)) {
                pending.addLast(raw);
            }
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = parameterized.getRawType() instanceof Class<?> value ? value : null;
            Type[] arguments = parameterized.getActualTypeArguments();
            if (raw != null && Map.class.isAssignableFrom(raw)) {
                containers.add(path + "<map-key>");
                containers.add(path + "<map-value>");
                inspectType(arguments[0], path + "<map-key>", pending, containers, unresolved);
                inspectType(arguments[1], path + "<map-value>", pending, containers, unresolved);
            } else if (raw != null && (Collection.class.isAssignableFrom(raw)
                    || Optional.class.isAssignableFrom(raw)
                    || AtomicReference.class.isAssignableFrom(raw))) {
                containers.add(path + "<element>");
                for (Type argument : arguments) {
                    inspectType(argument, path + "<element>", pending, containers, unresolved);
                }
            } else {
                if (raw != null && !terminal(raw)) pending.addLast(raw);
                for (Type argument : arguments) {
                    inspectType(argument, path + "<generic>", pending, containers, unresolved);
                }
            }
            return;
        }
        if (type instanceof GenericArrayType array) {
            containers.add(path + "[]");
            inspectType(array.getGenericComponentType(), path + "[]", pending, containers, unresolved);
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                inspectType(bound, path + "<wildcard>", pending, containers, unresolved);
            }
        } else if (type instanceof TypeVariable<?>) {
            unresolved.add(path + ":type-variable");
        }
    }

    private static boolean terminal(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type == String.class || type == UUID.class
                || Number.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class) {
            return true;
        }
        Package owner = type.getPackage();
        String name = owner == null ? "" : owner.getName();
        return name.startsWith("java.time") || name.startsWith("java.math")
                || name.startsWith("com.fasterxml.jackson") || name.startsWith("reactor.");
    }

    public record Graph(Set<Class<?>> roots, Set<Class<?>> models, Set<String> fields,
                        Set<String> containers, Set<String> annotatedReferences,
                        List<String> unresolvedGenericPaths) {
        public int modelCount() { return models.size(); }
        public int containerCount() { return containers.size(); }
    }
}
