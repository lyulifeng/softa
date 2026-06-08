package io.softa.framework.orm.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import io.softa.framework.orm.annotation.Field;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Abstract class of base model, with id and audit fields.
 *
 * <p>The audit fields carry {@code @Field} so the metadata scanner emits the
 * matching {@code sys_field} rows + DDL columns for every model — the scanner
 * walks the superclass chain, so the annotations are declared here once rather
 * than repeated on each entity. They are {@code readonly} / {@code nonCopyable}:
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

    @Field(label = "Creation time", readonly = true, nonCopyable = true)
    protected LocalDateTime createdTime;

    @Field(label = "Creator ID", readonly = true, nonCopyable = true)
    protected Long createdId;

    @Field(label = "Created By", readonly = true, nonCopyable = true, length = 64)
    protected String createdBy;

    @Field(label = "Update time", readonly = true, nonCopyable = true)
    protected LocalDateTime updatedTime;

    @Field(label = "Updater ID", readonly = true, nonCopyable = true)
    protected Long updatedId;

    @Field(label = "Updated By", readonly = true, nonCopyable = true, length = 64)
    protected String updatedBy;

}
