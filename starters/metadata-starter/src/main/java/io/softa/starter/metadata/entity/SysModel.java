package io.softa.starter.metadata.entity;

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
 * SysModel — metadata catalog row describing a Softa Model.
 *
 * <p>Self-described: this class carries {@code @Model} + per-field {@code @Field}
 * annotations, so the {@code MetadataAnnotationScanner} writes {@code sys_model}
 * rows describing {@code SysModel} itself (and its 7 siblings). The scanner
 * takes a pure-JDBC path against the physical {@code sys_*} tables and does
 * <b>not</b> read its own {@code ModelManager} state, so this self-reference
 * does not create a circular dependency.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "System Model",
        businessKey = {"modelName"},
        description = "Metadata catalog of models"
)
public class SysModel extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Label")
    private String label;

    @Field(label = "Model Name", required = true)
    private String modelName;

    @Field(label = "Display Name")
    private List<String> displayName;

    @Field(label = "Search Name")
    private List<String> searchName;

    @Field(label = "Default Order")
    private Orders defaultOrder;

    @Field(label = "Table Name")
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

    @Field(label = "Storage Type")
    private StorageType storageType;

    @Field(label = "Enable Version Lock")
    private Boolean versionLock;

    @Field(label = "Enable Multi-tenancy")
    private Boolean multiTenant;

    @Field(label = "Data Source")
    private String dataSource;

    @Field(label = "Service Name")
    private String serviceName;

    @Field(label = "Business Primary Key")
    private List<String> businessKey;

    @Field(label = "Partition Field")
    private String partitionField;

    @Field(label = "Description")
    private String description;

    @Field(label = "Ownership")
    private Ownership ownership;

    /**
     * One-to-many to {@link SysField} (joins on business key {@code modelName}, not id).
     * Has NO {@code sys_model} column — SysCatalog and the DDL builder both exclude
     * X-to-many — but is emitted as a {@code sys_field} row, so the meta-model is
     * self-describing. Populated in memory by {@code ModelManager}.
     */
    @Field(label = "Model Fields", fieldType = FieldType.ONE_TO_MANY, relatedModel = SysField.class, relatedField = "modelName")
    private List<SysField> modelFields;
}
