package com.footballmanager.application.service.world;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Machine-readable declaration of a type crossing a durable persistence boundary. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(WorldPersistedWriters.class)
public @interface WorldPersistedWriter {
    Class<?> root();
    String writeMethod();
    String storageFamily();
    DurabilityRole role();

    enum DurabilityRole {
        WORLD_REFERENCE_GRAPH,
        EXPLICITLY_NON_WORLD_REFERENCE
    }
}
