package io.softa.starter.permission.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for the repeatable {@link MainModelScope}. Never used directly —
 * stack multiple {@code @MainModelScope} annotations instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MainModelScopes {

    MainModelScope[] value();
}
