package io.softa.starter.permission.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for the repeatable {@link RequirePermission}. Never used directly —
 * stack multiple {@code @RequirePermission} annotations instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequirePermissions {

    RequirePermission[] value();
}
