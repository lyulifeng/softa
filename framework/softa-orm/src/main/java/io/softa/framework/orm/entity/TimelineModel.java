package io.softa.framework.orm.entity;

import java.io.Serial;
import java.time.LocalDate;
import io.softa.framework.orm.annotation.Field;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Abstract class of timeline model, with sliceId, effectiveStartDate and effectiveEndDate fields.
 *
 * <p>Like {@link AuditableModel}, these structural fields carry {@code @Field}
 * so the scanner (walking the superclass chain) emits the matching
 * {@code sys_field} rows + DDL columns once, rather than per entity. For a
 * timeline model {@code sliceId} is the physical primary-key column.
 *
 * <p>No {@code @Schema}: {@code @Model} / {@code @Field} are the single metadata
 * source and OpenAPI is generated from them by the softa-web apidocs customizers.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TimelineModel extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "Slice ID", readonly = true, nonCopyable = true)
    private Long sliceId;

    @Field(label = "Effective start date")
    private LocalDate effectiveStartDate;

    @Field(label = "Effective end date")
    private LocalDate effectiveEndDate;
}
