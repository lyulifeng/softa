package io.softa.starter.user.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * Permission entry — a single business capability (button / API action) under a navigation.
 * Default standard CRUD permissions (list / detail / create / update / ...) are
 * derived per navigation; custom actions declare their own permission rows with
 * explicit endpoints.
 *
 * <p><b>Metadata note:</b> {@code io.softa.starter.user.entity} is NOT in scanner-scope, so these
 * annotations are not reconciled at runtime — the authoritative metadata is the studio-managed
 * {@code sys_*}. Annotations mirror the live {@code sys_field} for documentation / future scanning.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(idStrategy = IdStrategy.EXTERNAL_ID, displayName = {"name"}, searchName = {"name"})
@Index(indexName = "idx_permission_navigation", fields = {"navigationId"})
public class Permission extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID", length = 128, description = "Permission ID (e.g. core-hr.employee.employee.transfer)")
    private String id;

    @Field(label = "Navigation ID", fieldType = FieldType.MANY_TO_ONE, relatedModel = Navigation.class,
            description = "Navigation ID this permission belongs to (FK navigation.id)")
    private String navigationId;

    @Field(length = 128, description = "Display name shown in admin Wizard (e.g. 'Transfer Employee')")
    private String name;

    @Field(description = "Explicit endpoint list for non-conventional URLs; null means EndpointIndex derives by convention. "
            + "Format: ['POST /<Model>/<action>', ...] — NO /api prefix (EndpointIndex matches against servletPath which is "
            + "already stripped of the app context by Spring). Path must start with '/'; entries with a leading '/api' or "
            + "missing '/' are rejected at startup.")
    private JsonNode endpoints;

    @Field(length = 256, description = "Optional description")
    private String description;
}
