package com.footballmanager.domain.model.metadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the semantic identity carried by a durable field or one side of a
 * durable container. Routes are structural, for example VALUE, MAP_KEY,
 * MAP_VALUE/ELEMENT, or ELEMENT/MAP_VALUE.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Repeatable(WorldIdentityReferences.class)
public @interface WorldIdentityReference {
    WorldIdentityDomain domain();
    String route() default "VALUE";
    boolean nullable() default false;
}
