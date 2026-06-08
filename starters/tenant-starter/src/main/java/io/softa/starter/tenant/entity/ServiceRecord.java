package io.softa.starter.tenant.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * ServiceRecord Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Service Record",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true
)
public class ServiceRecord extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "User")
    private Long userId;

    @Field(label = "Service Product")
    private Long serviceId;

    @Field(label = "Order ID")
    private Long orderId;

    @Field(label = "Request Data", required = true)
    private JsonNode requestData;

    @Field(label = "Result Summary", length = 3000)
    private String resultSummary;

    @Field(label = "Result Detail", length = 20000)
    private String resultDetail;

    @Field(label = "Deleted")
    private Boolean deleted;
}
