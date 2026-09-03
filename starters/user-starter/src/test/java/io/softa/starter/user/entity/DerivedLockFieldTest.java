package io.softa.starter.user.entity;

import org.junit.jupiter.api.Test;

import io.softa.framework.orm.annotation.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserAccount#getLocked()} is derived on read and must not be writable.
 *
 * <p>Asserted on the annotation because that is where the decision is made and where it is easy to
 * lose: {@code AnnotationParser} copies {@code readonly} into {@code sys_field}, and
 * {@code ModelManager.getModelUpdatableFields} — the set {@code ModelServiceImpl} intersects an
 * update payload with — filters exactly on it. {@code dynamic} alone does not: it keeps the column
 * out of the DDL, not out of the UPDATE.
 */
class DerivedLockFieldTest {

    @Test
    void theDerivedLockIsNotWritable() throws NoSuchFieldException {
        Field annotation = UserAccount.class.getDeclaredField("locked").getAnnotation(Field.class);

        // Load-bearing: without this, POST /UserAccount/updateOne {"id":…,"locked":true} reaches
        // UPDATE user_account SET locked = ? — an orphaned legacy column on an upgraded database,
        // and an unknown column on a fresh one.
        assertThat(annotation.readonly()).isTrue();
        // Still derived, still out of the DDL — the two attributes answer different questions.
        assertThat(annotation.dynamic()).isTrue();
    }
}
