package io.softa.framework.orm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeated {@link Index} declarations.
 *
 * <p>Required by Java's {@code @Repeatable} mechanism. You don't write
 * {@code @Indexes(...)} directly — stack {@code @Index} on the class and the
 * compiler synthesizes this container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Indexes {
    Index[] value();
}
