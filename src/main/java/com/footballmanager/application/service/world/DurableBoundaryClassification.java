package com.footballmanager.application.service.world;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Explicit classification for a discovered durable boundary outside World V2. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DurableBoundaryClassification {
    DurablePersistenceBoundary.Classification value();
    String reason();
}
