package io.softa.starter.studio.meta.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.Ownership;
import io.softa.framework.orm.enums.StorageType;

/**
 * DesignModel Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        businessKey = {"modelName"},
        displayName = {"modelName", "label"},
        searchName = {"modelName", "label"},
        defaultOrder = "modelName"
)
public class DesignModel extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Portfolio")
    private Long portfolioId;

    @Field(label = "App ID")
    private Long appId;

    @Field(required = true)
    private String label;

    @Field(required = true)
    private String modelName;

    @Field
    private List<String> displayName;

    @Field
    private List<String> searchName;

    @Field
    private Orders defaultOrder;

    @Field
    private String tableName;

    @Field(label = "Enable Soft Delete")
    private Boolean softDelete;

    @Field(label = "Soft Delete Field Name")
    private String softDeleteField;

    @Field(label = "Enable Active Control")
    private Boolean activeControl;

    @Field(label = "Is Timeline Model")
    private Boolean timeline;

    @Field(label = "ID Strategy")
    private IdStrategy idStrategy;

    @Field
    private StorageType storageType;

    @Field(label = "Enable Version Lock")
    private Boolean versionLock;

    @Field(label = "Enable Multi-tenancy")
    private Boolean multiTenant;

    // Initialized to true (the column is NOT NULL DEFAULT 1) so hand-constructed
    // instances never insert NULL.
    @Field(defaultValue = "true")
    private Boolean copyable = Boolean.TRUE;

    @Field
    private String dataSource;

    @Field
    private String serviceName;

    @Field(label = "Business Primary Key")
    private List<String> businessKey;

    @Field
    private String partitionField;

    @Field(length = 256)
    private String description;

    @Field
    private Ownership ownership;

    @Field(fieldType = FieldType.ONE_TO_MANY,
            relatedModel = DesignField.class, relatedField = "modelName")
    private List<DesignField> modelFields;

    @Field(fieldType = FieldType.ONE_TO_MANY,
            relatedModel = DesignModelIndex.class, relatedField = "modelName")
    private List<DesignModelIndex> modelIndexes;

    @Field
    private Boolean deleted;
}
