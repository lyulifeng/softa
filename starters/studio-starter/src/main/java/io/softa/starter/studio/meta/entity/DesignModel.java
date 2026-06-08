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
        label = "Design Model",
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

    @Field(label = "Label", length = 64, required = true)
    private String label;

    @Field(label = "Model Name", length = 64, required = true)
    private String modelName;

    @Field(label = "Display Name", length = 255)
    private List<String> displayName;

    @Field(label = "Search Name", length = 255)
    private List<String> searchName;

    @Field(label = "Default Order")
    private Orders defaultOrder;

    @Field(label = "Table Name", length = 64)
    private String tableName;

    @Field(label = "Enable Soft Delete")
    private Boolean softDelete;

    @Field(label = "Soft Delete Field Name", length = 64)
    private String softDeleteField;

    @Field(label = "Enable Active Control")
    private Boolean activeControl;

    @Field(label = "Is Timeline Model")
    private Boolean timeline;

    @Field(label = "ID Strategy")
    private IdStrategy idStrategy;

    @Field(label = "Storage Type")
    private StorageType storageType;

    @Field(label = "Enable Version Lock")
    private Boolean versionLock;

    @Field(label = "Enable Multi-tenancy")
    private Boolean multiTenant;

    @Field(label = "Data Source", length = 64)
    private String dataSource;

    @Field(label = "Service Name", length = 64)
    private String serviceName;

    @Field(label = "Business Primary Key", length = 255)
    private List<String> businessKey;

    @Field(label = "Partition Field", length = 64)
    private String partitionField;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Ownership")
    private Ownership ownership;

    @Field(label = "Model Fields", fieldType = FieldType.ONE_TO_MANY,
            relatedModel = DesignField.class, relatedField = "modelName")
    private List<DesignField> modelFields;

    @Field(label = "Model Indexes", fieldType = FieldType.ONE_TO_MANY,
            relatedModel = DesignModelIndex.class, relatedField = "modelName")
    private List<DesignModelIndex> modelIndexes;

    @Field(label = "Deleted")
    private Boolean deleted;
}
