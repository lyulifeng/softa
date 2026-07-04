package io.softa.framework.orm.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;

/**
 * Abstract class of base model, with id and audit fields.
 *
 * <p>The audit fields carry {@code @Field} so the metadata scanner emits the
 * matching {@code sys_field} rows + DDL columns for every model — the scanner
 * walks the superclass chain, so the annotations are declared here once rather
 * than repeated on each entity. They are {@code readonly} / {@code copyable = false}:
 * populated by the framework on create/update, never by user input or copy.
 *
 * <p>No {@code @Schema}: {@code @Model} / {@code @Field} are the single metadata
 * source and OpenAPI is generated from them by the softa-web apidocs customizers.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AuditableModel extends AbstractModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(readonly = true, copyable = false)
    protected LocalDateTime createdTime;

    @Field(readonly = true, copyable = false)
    protected Long createdId;

    @Field(readonly = true, copyable = false)
    protected String createdBy;

    @Field(readonly = true, copyable = false)
    protected LocalDateTime updatedTime;

    @Field(readonly = true, copyable = false)
    protected Long updatedId;

    @Field(readonly = true, copyable = false)
    protected String updatedBy;

}
