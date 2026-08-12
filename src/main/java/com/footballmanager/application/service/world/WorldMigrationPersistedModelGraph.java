package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.metadata.WorldIdentityReference;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Complete structural inventory reachable from persistence-writer roots. */
public final class WorldMigrationPersistedModelGraph {

    private static final Map<String, Set<Class<?>>> COMPATIBILITY_OVERRIDES = Map.of(
            "CareerSave.teamStarting11Subdivision", Set.of(String.class,
                    com.footballmanager.domain.model.valueobject.LineupSlot.class),
            "MatchCommand.payload", Set.of(String.class));

    private final WorldMigrationPersistedRootAuthority rootAuthority;

    public WorldMigrationPersistedModelGraph() {
        this(new WorldMigrationPersistedRootAuthority());
    }

    WorldMigrationPersistedModelGraph(WorldMigrationPersistedRootAuthority rootAuthority) {
        this.rootAuthority = rootAuthority;
    }

    public Graph discover() {
        return inspect(rootAuthority.discover().graphRoots(), COMPATIBILITY_OVERRIDES);
    }

    public Graph inspect(Set<Class<?>> roots, Map<String, Set<Class<?>>> overrides) {
        Set<Class<?>> models = new LinkedHashSet<>();
        Set<String> fields = new LinkedHashSet<>();
        Set<String> inheritedFields = new LinkedHashSet<>();
        Set<String> containers = new LinkedHashSet<>();
        Set<String> identityLeaves = new LinkedHashSet<>();
        Set<String> classifiedIdentityPaths = new LinkedHashSet<>();
        Set<String> annotatedReferences = new LinkedHashSet<>();
        List<String> unresolved = new ArrayList<>();
        Deque<TypeNode> pending = new ArrayDeque<>();
        roots.stream().sorted(Comparator.comparing(Class::getName))
                .forEach(root -> pending.addLast(new TypeNode(root, Map.of())));
        Set<String> visited = new LinkedHashSet<>();

        while (!pending.isEmpty()) {
            TypeNode node = pending.removeFirst();
            if (terminal(node.raw()) || !visited.add(node.signature())) continue;
            models.add(node.raw());
            inspectHierarchy(node, models, fields, inheritedFields, containers,
                    identityLeaves, classifiedIdentityPaths, annotatedReferences,
                    unresolved, pending, overrides);
        }

        Set<String> staleClassifications = new LinkedHashSet<>(classifiedIdentityPaths);
        staleClassifications.removeAll(identityLeaves);
        staleClassifications.forEach(path -> unresolved.add(path + ":classification-without-identity-leaf"));

        return new Graph(Set.copyOf(roots), Set.copyOf(models), Set.copyOf(fields),
                Set.copyOf(inheritedFields), Set.copyOf(containers), Set.copyOf(identityLeaves),
                Set.copyOf(classifiedIdentityPaths), Set.copyOf(annotatedReferences),
                List.copyOf(unresolved));
    }

    private static void inspectHierarchy(TypeNode node, Set<Class<?>> models,
                                         Set<String> fields, Set<String> inheritedFields,
                                         Set<String> containers, Set<String> identityLeaves,
                                         Set<String> classifiedIdentityPaths,
                                         Set<String> annotatedReferences, List<String> unresolved,
                                         Deque<TypeNode> pending, Map<String, Set<Class<?>>> overrides) {
        Class<?> holder = node.raw();
        Class<?> declaring = holder;
        Map<TypeVariable<?>, Type> bindings = new LinkedHashMap<>(node.bindings());
        while (declaring != null && declaring != Object.class) {
            List<Field> declared = new ArrayList<>(List.of(declaring.getDeclaredFields()));
            declared.sort(Comparator.comparing(Field::getName));
            for (Field field : declared) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                        || Modifier.isTransient(field.getModifiers())) continue;
                String path = holder.getSimpleName() + "." + field.getName();
                fields.add(path);
                if (declaring != holder) inheritedFields.add(path + "<-" + declaring.getSimpleName());

                Set<Class<?>> override = overrides.getOrDefault(path, Set.of());
                if (!override.isEmpty()) containers.add(path + "@COMPATIBILITY_UNION");
                Type resolved = resolve(field.getGenericType(), bindings, path, unresolved);
                inspectType(resolved, path, "VALUE", pending, containers, identityLeaves, unresolved, override);

                for (WorldIdentityReference marker : field.getAnnotationsByType(WorldIdentityReference.class)) {
                    String classifiedPath = path + "@" + normalizeRoute(marker.route());
                    classifiedIdentityPaths.add(classifiedPath);
                    if (marker.domain().isReference()) {
                        annotatedReferences.add(classifiedPath + ":" + marker.domain());
                    }
                }
            }
            Type genericSuper = declaring.getGenericSuperclass();
            if (genericSuper == null || genericSuper == Object.class) break;
            Type resolvedSuper = resolve(genericSuper, bindings,
                    holder.getSimpleName() + ".<super>", unresolved);
            if (resolvedSuper instanceof ParameterizedType parameterized
                    && parameterized.getRawType() instanceof Class<?> rawSuper) {
                bindings = bindingsFor(rawSuper, parameterized.getActualTypeArguments());
                models.add(rawSuper);
                declaring = rawSuper;
            } else if (resolvedSuper instanceof Class<?> rawSuper) {
                bindings = Map.of();
                models.add(rawSuper);
                declaring = rawSuper;
            } else {
                unresolved.add(holder.getSimpleName() + ".<super>:unresolved");
                break;
            }
        }
    }

    private static void inspectType(Type type, String path, String route, Deque<TypeNode> pending,
                                    Set<String> containers, Set<String> identityLeaves,
                                    List<String> unresolved, Set<Class<?>> objectOverrides) {
        if (type instanceof Class<?> raw) {
            if (raw == String.class || raw == UUID.class) {
                identityLeaves.add(path + "@" + route);
            } else if (raw.isArray()) {
                String next = append(route, "ELEMENT");
                containers.add(path + "@" + next);
                inspectType(raw.getComponentType(), path, next, pending, containers, identityLeaves, unresolved,
                        objectOverrides);
            } else if (raw == Object.class) {
                if (objectOverrides.isEmpty()) {
                    unresolved.add(path + "@" + route + ":raw-object");
                } else {
                    for (Class<?> candidate : objectOverrides) {
                        if (candidate == String.class || candidate == UUID.class) {
                            identityLeaves.add(path + "@" + route);
                        } else if (!terminal(candidate)) {
                            pending.addLast(new TypeNode(candidate, Map.of()));
                        }
                    }
                }
            } else if (!terminal(raw)) {
                pending.addLast(new TypeNode(raw, Map.of()));
            }
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            if (!(parameterized.getRawType() instanceof Class<?> raw)) {
                unresolved.add(path + "@" + route + ":unknown-raw-type");
                return;
            }
            Type[] arguments = parameterized.getActualTypeArguments();
            if (Map.class.isAssignableFrom(raw)) {
                inspectContainer(arguments[0], path, append(route, "MAP_KEY"), pending,
                        containers, identityLeaves, unresolved, objectOverrides);
                inspectContainer(arguments[1], path, append(route, "MAP_VALUE"), pending,
                        containers, identityLeaves, unresolved, objectOverrides);
            } else if (Collection.class.isAssignableFrom(raw)
                    || Optional.class.isAssignableFrom(raw)
                    || AtomicReference.class.isAssignableFrom(raw)) {
                for (Type argument : arguments) {
                    inspectContainer(argument, path, append(route, "ELEMENT"), pending,
                            containers, identityLeaves, unresolved, objectOverrides);
                }
            } else {
                pending.addLast(new TypeNode(raw, bindingsFor(raw, arguments)));
            }
            return;
        }
        if (type instanceof GenericArrayType array) {
            String next = append(route, "ELEMENT");
            inspectContainer(array.getGenericComponentType(), path, next, pending,
                    containers, identityLeaves, unresolved, objectOverrides);
        } else if (type instanceof TypeVariable<?> variable) {
            unresolved.add(path + "@" + route + ":unresolved-type-variable:" + variable.getName());
        } else if (type instanceof WildcardType wildcard) {
            Type[] lower = wildcard.getLowerBounds();
            Type[] upper = wildcard.getUpperBounds();
            if (lower.length == 0 && (upper.length == 0 || upper[0] == Object.class)) {
                unresolved.add(path + "@" + route + ":unbounded-wildcard");
            } else {
                for (Type bound : lower.length == 0 ? upper : lower) {
                    inspectType(bound, path, route, pending, containers, identityLeaves, unresolved,
                            objectOverrides);
                }
            }
        } else {
            unresolved.add(path + "@" + route + ":unsupported-type:" + type.getTypeName());
        }
    }

    private static void inspectContainer(Type type, String path, String route, Deque<TypeNode> pending,
                                         Set<String> containers, Set<String> identityLeaves,
                                         List<String> unresolved, Set<Class<?>> objectOverrides) {
        containers.add(path + "@" + route);
        inspectType(type, path, route, pending, containers, identityLeaves, unresolved, objectOverrides);
    }

    private static Type resolve(Type type, Map<TypeVariable<?>, Type> bindings,
                                String path, List<String> unresolved) {
        if (type instanceof TypeVariable<?> variable) {
            Type bound = bindings.get(variable);
            if (bound == null || bound == variable) return variable;
            return resolve(bound, bindings, path, unresolved);
        }
        if (type instanceof ParameterizedType parameterized) {
            Type[] actual = parameterized.getActualTypeArguments();
            Type[] resolved = new Type[actual.length];
            for (int i = 0; i < actual.length; i++) {
                resolved[i] = resolve(actual[i], bindings, path, unresolved);
            }
            return new ResolvedParameterizedType(parameterized.getRawType(), resolved,
                    parameterized.getOwnerType());
        }
        if (type instanceof GenericArrayType array) {
            return new ResolvedGenericArrayType(resolve(array.getGenericComponentType(), bindings,
                    path, unresolved));
        }
        return type;
    }

    private static Map<TypeVariable<?>, Type> bindingsFor(Class<?> raw, Type[] arguments) {
        TypeVariable<?>[] variables = raw.getTypeParameters();
        Map<TypeVariable<?>, Type> result = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(variables.length, arguments.length); i++) {
            result.put(variables[i], arguments[i]);
        }
        return Map.copyOf(result);
    }

    private static String normalizeRoute(String route) {
        String normalized = route == null ? "" : route.trim().toUpperCase();
        if (normalized.isEmpty()) throw new IllegalStateException("Identity route must not be empty");
        return normalized;
    }

    private static String append(String route, String segment) {
        return "VALUE".equals(route) ? segment : route + "/" + segment;
    }

    private static boolean terminal(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type == String.class || type == UUID.class
                || Number.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class) {
            return true;
        }
        Package owner = type.getPackage();
        String name = owner == null ? "" : owner.getName();
        return name.startsWith("java.time") || name.startsWith("java.math")
                || name.startsWith("com.fasterxml.jackson") || name.startsWith("reactor.")
                || name.startsWith("org.slf4j");
    }

    private record TypeNode(Class<?> raw, Map<TypeVariable<?>, Type> bindings) {
        String signature() {
            return raw.getName() + bindings.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                    .map(entry -> "|" + entry.getKey().getName() + "=" + entry.getValue().getTypeName())
                    .reduce("", String::concat);
        }
    }

    private record ResolvedParameterizedType(Type rawType, Type[] actualTypeArguments,
                                             Type ownerType) implements ParameterizedType {
        @Override public Type[] getActualTypeArguments() { return actualTypeArguments.clone(); }
        @Override public Type getRawType() { return rawType; }
        @Override public Type getOwnerType() { return ownerType; }
        @Override public String getTypeName() { return rawType.getTypeName(); }
    }

    private record ResolvedGenericArrayType(Type genericComponentType) implements GenericArrayType {
        @Override public Type getGenericComponentType() { return genericComponentType; }
    }

    public record Graph(Set<Class<?>> roots, Set<Class<?>> models, Set<String> fields,
                        Set<String> inheritedFields, Set<String> containers,
                        Set<String> identityLeaves, Set<String> classifiedIdentityPaths,
                        Set<String> annotatedReferences, List<String> unresolvedGenericPaths) {
        public int modelCount() { return models.size(); }
        public int containerCount() { return containers.size(); }
    }
}
