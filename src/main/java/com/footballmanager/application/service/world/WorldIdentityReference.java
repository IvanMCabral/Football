package com.footballmanager.application.service.world;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Explicit semantic marker used by the persisted-model graph; field names are never guessed. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface WorldIdentityReference {
    WorldMigrationDurableReferenceRegistry.Role role();
    Location[] locations() default { Location.VALUE };

    enum Location { VALUE, ELEMENT, MAP_KEY, MAP_VALUE }
}
