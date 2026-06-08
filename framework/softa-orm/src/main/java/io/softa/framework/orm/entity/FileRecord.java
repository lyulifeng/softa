package io.softa.framework.orm.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
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
        label = "File Record",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true
)
public class FileRecord extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "File Name", required = true, length = 128)
    private String fileName;

    @Field(label = "OSS Key", length = 128)
    private String ossKey;

    @Field(label = "File Type")
    private FileType fileType;

    @Field(label = "File Size(KB)")
    private Integer fileSize;

    @Field(label = "Checksum", length = 64)
    private String checksum;

    @Field(label = "Model Name", length = 64)
    private String modelName;

    @Field(label = "Row ID", length = 64)
    private String rowId;

    @Field(label = "Field Name", length = 64)
    private String fieldName;

    @Field(label = "Source")
    private FileSource source;

    @Field(label = "Deleted")
    private Boolean deleted;
}
