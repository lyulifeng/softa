package io.softa.starter.studio.release.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.starter.studio.release.enums.DesignPortfolioStatus;

/**
 * DesignPortfolio Model
 */
@Data
@Model(idStrategy = IdStrategy.DISTRIBUTED_LONG)
@EqualsAndHashCode(callSuper = true)
public class DesignPortfolio extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Owner")
    private Long ownerId;

    @Field(required = true)
    private String name;

    @Field
    private String code;

    @Field
    private DesignPortfolioStatus status;

    @Field(length = 256)
    private String description;

    @Field
    private Boolean deleted;
}
