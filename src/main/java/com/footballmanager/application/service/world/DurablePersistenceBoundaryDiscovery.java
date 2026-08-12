package com.footballmanager.application.service.world;

import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers physical durable adapters from class structure and bytecode.
 * Classification annotations are read only after a class has been admitted as
 * a boundary. This makes an unannotated writer observable and fail-closed.
 */
public final class DurablePersistenceBoundaryDiscovery {

    private static final String PRODUCT_BASE_PACKAGE = "com.footballmanager";
    private static final Set<String> REDIS_MARKERS = Set.of(
            "org/springframework/data/redis",
            "ReactiveRedisTemplate",
            "ReactiveStringRedisTemplate",
            "RedisTemplate",
            "ReactiveRedisConnection");
    private static final Set<String> POSTGRES_MARKERS = Set.of(
            "org/springframework/r2dbc",
            "org/springframework/data/r2dbc",
            "Database" + "Client",
            "R2dbcEntityTemplate",
            "R2dbcRepository",
            "org/springframework/data/repository");

    public Discovery discover() {
        Set<Class<?>> candidates = new LinkedHashSet<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
            CachingMetadataReaderFactory metadataFactory = new CachingMetadataReaderFactory(resolver);
            Resource[] resources = resolver.getResources("classpath*:com/footballmanager/**/*.class");
            for (Resource resource : resources) {
                String className = metadataFactory.getMetadataReader(resource).getClassMetadata().getClassName();
                if (!className.startsWith(PRODUCT_BASE_PACKAGE + ".") || className.contains("module-info")) continue;
                addCandidate(candidates, className, loader);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot enumerate durable boundary classpath", error);
        }
        return inspect(candidates, false);
    }

    private static void addCandidate(Set<Class<?>> candidates, String className, ClassLoader loader) {
            try {
                Class<?> type = Class.forName(className, false, loader);
                if (isProductClass(type)) candidates.add(type);
            } catch (ClassNotFoundException error) {
                throw new IllegalStateException("Cannot inspect durable boundary "
                        + className, error);
            }
    }

    public Discovery inspect(Set<Class<?>> candidates) {
        return inspect(candidates, true);
    }

    private Discovery inspect(Set<Class<?>> candidates, boolean includeNestedCandidates) {
        List<DurablePersistenceBoundary> boundaries = new ArrayList<>();
        for (Class<?> candidate : candidates) {
            if (isDurableBoundary(candidate, includeNestedCandidates)) boundaries.add(describe(candidate));
        }
        boundaries.sort(Comparator.comparing(boundary -> boundary.boundaryType().getName()));
        List<DurablePersistenceBoundary> unclassified = boundaries.stream()
                .filter(boundary -> !boundary.isClassified())
                .toList();
        return new Discovery(List.copyOf(boundaries), unclassified);
    }

    private DurablePersistenceBoundary describe(Class<?> type) {
        DurablePersistenceBoundary.StorageTechnology technology = technology(type);
        List<DurablePersistenceBoundary.Declaration> declarations = new ArrayList<>();
        Set<Class<?>> persistedTypes = new LinkedHashSet<>();
        WorldPersistedWriter[] writers = type.getAnnotationsByType(WorldPersistedWriter.class);
        for (WorldPersistedWriter writer : writers) {
            DurablePersistenceBoundary.Classification classification = writer.role()
                    == WorldPersistedWriter.DurabilityRole.WORLD_REFERENCE_GRAPH
                    ? DurablePersistenceBoundary.Classification.WORLD_REFERENCE_RELEVANT
                    : DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_NON_WORLD_IDENTITY;
            String reason = writer.role() == WorldPersistedWriter.DurabilityRole.WORLD_REFERENCE_GRAPH
                    ? "declared World V2 persisted graph writer"
                    : "declared non-World durable writer";
            declarations.add(new DurablePersistenceBoundary.Declaration(writer.writeMethod(), writer.root(),
                    writer.storageFamily(), classification, reason));
            persistedTypes.add(writer.root());
        }
        DurableBoundaryClassification explicit = type.getAnnotation(DurableBoundaryClassification.class);
        if (explicit != null && writers.length > 0) {
            boolean compatible = java.util.Arrays.stream(writers).allMatch(writer -> {
                DurablePersistenceBoundary.Classification declared = writer.role()
                        == WorldPersistedWriter.DurabilityRole.WORLD_REFERENCE_GRAPH
                        ? DurablePersistenceBoundary.Classification.WORLD_REFERENCE_RELEVANT
                        : DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_NON_WORLD_IDENTITY;
                return declared == explicit.value();
            });
            if (!compatible) {
                throw new IllegalStateException("Conflicting durable classifications on " + type.getName());
            }
        }
        DurablePersistenceBoundary.Classification classification;
        String reason;
        DurablePersistenceBoundary.ClassificationSource source;
        if (explicit != null) {
            classification = explicit.value();
            reason = explicit.reason();
            source = DurablePersistenceBoundary.ClassificationSource.DURABLE_BOUNDARY_CLASSIFICATION;
        } else if (writers.length > 0) {
            classification = declarations.stream().anyMatch(value -> value.classification()
                    == DurablePersistenceBoundary.Classification.WORLD_REFERENCE_RELEVANT)
                    ? DurablePersistenceBoundary.Classification.WORLD_REFERENCE_RELEVANT
                    : DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_NON_WORLD_IDENTITY;
            reason = declarations.getFirst().reason();
            source = DurablePersistenceBoundary.ClassificationSource.WORLD_PERSISTED_WRITER;
        } else {
            classification = DurablePersistenceBoundary.Classification.UNCLASSIFIED;
            reason = "durable boundary discovered without classification metadata";
            source = DurablePersistenceBoundary.ClassificationSource.UNCLASSIFIED_DISCOVERY;
        }
        if (explicit != null) {
            declarations.add(new DurablePersistenceBoundary.Declaration("boundary", Object.class,
                    technology.name(), classification, reason));
        }
        return new DurablePersistenceBoundary(type, technology, operationType(type),
                Set.copyOf(persistedTypes), List.copyOf(declarations), classification, reason, source);
    }

    private static boolean isDurableBoundary(Class<?> type, boolean includeNestedCandidates) {
        // Compiler-generated/member helpers can reference a durable template while
        // owning no persistence boundary. They remain visible only when explicitly
        // classified, avoiding false positives without hiding real top-level writers.
        if (!includeNestedCandidates && (type.isMemberClass() || type.isAnonymousClass())
                && !type.isAnnotationPresent(WorldPersistedWriter.class)
                && !type.isAnnotationPresent(DurableBoundaryClassification.class)) return false;
        if (type.isAnnotationPresent(WorldPersistedWriter.class)
                || type.isAnnotationPresent(DurableBoundaryClassification.class)) return true;
        return hasDurableTypeInHierarchy(type) || bytecodeReferencesDurableApi(type);
    }

    private static boolean hasDurableTypeInHierarchy(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (durableType(current)) return true;
            for (Class<?> contract : current.getInterfaces()) if (durableType(contract)) return true;
        }
        return false;
    }

    private static boolean durableType(Class<?> type) {
        String name = type.getName();
        return name.contains("ReactiveRedisTemplate") || name.contains("RedisTemplate")
                || name.contains("Database" + "Client") || name.contains("R2dbcEntityTemplate")
                || org.springframework.data.repository.Repository.class.isAssignableFrom(type)
                || type.getName().contains("R2dbcRepository");
    }

    private static boolean bytecodeReferencesDurableApi(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) return false;
            ClassReader reader = new ClassReader(input);
            DurableReferenceVisitor visitor = new DurableReferenceVisitor();
            reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor.durable;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot inspect durable boundary bytecode " + type.getName(), error);
        }
    }

    private static DurablePersistenceBoundary.StorageTechnology technology(Class<?> type) {
        return hasMarker(type, POSTGRES_MARKERS)
                ? DurablePersistenceBoundary.StorageTechnology.POSTGRESQL
                : DurablePersistenceBoundary.StorageTechnology.REDIS;
    }

    private static boolean hasMarker(Class<?> type, Set<String> markers) {
        String name = type.getName().replace('.', '/');
        if (markers.stream().anyMatch(name::contains)) return true;
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) return false;
            ClassReader reader = new ClassReader(input);
            MarkerVisitor visitor = new MarkerVisitor(markers);
            reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor.found;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot classify durable technology " + type.getName(), error);
        }
    }

    private static DurablePersistenceBoundary.OperationType operationType(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) return DurablePersistenceBoundary.OperationType.UNKNOWN;
            ClassReader reader = new ClassReader(input);
            OperationVisitor visitor = new OperationVisitor();
            reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (visitor.read && visitor.write) return DurablePersistenceBoundary.OperationType.READ_WRITE;
            if (visitor.write) return DurablePersistenceBoundary.OperationType.WRITE;
            if (visitor.read) return DurablePersistenceBoundary.OperationType.READ;
            return DurablePersistenceBoundary.OperationType.UNKNOWN;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot inspect durable operation " + type.getName(), error);
        }
    }

    private static boolean isProductClass(Class<?> type) {
        var source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) return true;
        return !source.getLocation().toExternalForm().replace('\\', '/').contains("/test-classes/");
    }

    public record Discovery(List<DurablePersistenceBoundary> boundaries,
                            List<DurablePersistenceBoundary> unclassified) {
        public long redisCount() { return boundaries.stream().filter(value ->
                value.technology() == DurablePersistenceBoundary.StorageTechnology.REDIS).count(); }
        public long postgresCount() { return boundaries.stream().filter(value ->
                value.technology() == DurablePersistenceBoundary.StorageTechnology.POSTGRESQL).count(); }
    }

    private static final class DurableReferenceVisitor extends ClassVisitor {
        private boolean durable;
        private DurableReferenceVisitor() { super(Opcodes.ASM9); }
        @Override public void visit(int version, int access, String name, String signature,
                                    String superName, String[] interfaces) {
            durable |= marker(superName) || (interfaces != null && java.util.Arrays.stream(interfaces).anyMatch(this::marker));
            super.visit(version, access, name, signature, superName, interfaces);
        }
        @Override public org.springframework.asm.FieldVisitor visitField(int access, String name, String descriptor,
                                                                           String signature, Object value) {
            durable |= marker(descriptor);
            return super.visitField(access, name, descriptor, signature, value);
        }
        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                   String signature, String[] exceptions) {
            durable |= marker(descriptor);
            return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                                       boolean isInterface) {
                    durable |= marker(owner) || marker(descriptor);
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
                @Override public void visitTypeInsn(int opcode, String type) {
                    durable |= marker(type);
                    super.visitTypeInsn(opcode, type);
                }
            };
        }
        private boolean marker(String value) {
            return value != null && (containsMarker(value, REDIS_MARKERS) || containsMarker(value, POSTGRES_MARKERS));
        }
    }

    private static final class MarkerVisitor extends ClassVisitor {
        private final Set<String> markers;
        private boolean found;
        private MarkerVisitor(Set<String> markers) { super(Opcodes.ASM9); this.markers = markers; }
        @Override public void visit(int version, int access, String name, String signature,
                                    String superName, String[] interfaces) {
            found |= containsMarker(superName, markers) || (interfaces != null && java.util.Arrays.stream(interfaces)
                    .anyMatch(value -> containsMarker(value, markers)));
            super.visit(version, access, name, signature, superName, interfaces);
        }
        @Override public org.springframework.asm.FieldVisitor visitField(int access, String name, String descriptor,
                                                                           String signature, Object value) {
            found |= containsMarker(descriptor, markers);
            return super.visitField(access, name, descriptor, signature, value);
        }
        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                   String signature, String[] exceptions) {
            found |= containsMarker(descriptor, markers);
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }

    private static final class OperationVisitor extends ClassVisitor {
        private boolean read;
        private boolean write;
        private OperationVisitor() { super(Opcodes.ASM9); }
        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                   String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                @Override public void visitMethodInsn(int opcode, String owner, String method,
                                                       String descriptor, boolean isInterface) {
                    String lower = method.toLowerCase(java.util.Locale.ROOT);
                    write |= lower.contains("save") || lower.contains("insert") || lower.contains("update")
                            || lower.contains("delete") || lower.contains("set") || lower.contains("add");
                    read |= lower.contains("find") || lower.contains("get") || lower.contains("exists")
                            || lower.contains("load") || lower.contains("query");
                    super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                }
            };
        }
    }

    private static boolean containsMarker(String value, Set<String> markers) {
        if (value == null) return false;
        return markers.stream().anyMatch(value::contains);
    }
}
