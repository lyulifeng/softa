package io.softa.framework.orm.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.enums.FileSource;
import io.softa.framework.orm.enums.FileType;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * FileRecord Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true
)
// Serves the vacated-slot release read on every attachment-carrying update
// (model_name = ? AND field_name = ? AND row_id IN ...) — this table grows with every upload and export.
@Index(fields = {"modelName", "rowId"})
// One row per stored object. Live databases already carry this as uk_file_record_oss_key —
// hand-created, because this entity's package was outside scanner-scope and the table was
// therefore built by hand. Declaring it here is what keeps it once the scanner takes the table
// over: an undeclared index on a converged table is DROPPED. The derived name is
// uk_<table>_<column> = uk_file_record_oss_key, so this matches the existing index rather
// than replacing it. Nullable column: both MySQL and PostgreSQL treat each NULL as distinct,
// so rows that have no object yet do not collide.
@Index(fields = {"ossKey"}, unique = true)
public class FileRecord extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(required = true, length = 128)
    private String fileName;

    @Field(label = "OSS Key", length = 128)
    private String ossKey;

    @Field
    private FileType fileType;

    @Field(label = "File Size(KB)")
    private Integer fileSize;

    @Field
    private String checksum;

    @Field
    private String modelName;

    @Field(label = "Row ID")
    private String rowId;

    @Field
    private String fieldName;

    @Field
    private FileSource source;

    @Field
    private Boolean deleted;
}
