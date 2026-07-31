package io.softa.starter.user.entity;

import java.io.Serial;

import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.OnDelete;

/**
 * Which companies a role may reach. One row per (role, company).
 *
 * <p>A <b>grant</b>, not a view. It bounds what a user can see at all and is configured by an
 * administrator; which of the granted companies the user is looking at right now is the separate,
 * per-request header selection applied by {@code CompanyScope}. The two compose: the grant bounds the
 * set, the selection picks one out of it.
 *
 * <p><b>No rows means no restriction, not no access.</b> The grant is opt-in: a role nobody has
 * configured keeps seeing every company its other permissions allow. Fail-closed would be the
 * safer-sounding default and the wrong one here — it would silently empty every screen for every
 * existing role the moment this ships. Same convention as {@link RoleNavigation#getPermissionIds()},
 * where absent means all and a subset means deliberately trimmed.
 *
 * <p><b>Why "company" and not the name the product uses.</b> The product calls this a legal entity, and
 * so does the model that holds it — but that model is HR's, and this module cannot see it (see
 * {@link #getCompanyId()}). Which model is "the company" binds in exactly one place,
 * {@link ModelConstant#COMPANY_MODEL}, deliberately: its own javadoc says one constant, so the
 * company-scoped narrowing and the request enricher cannot drift apart on what the company is. Spelling
 * that binding a second time in a class, column and table name is the drift the constant exists to
 * prevent, so everything in this layer says company — {@code CompanyScope},
 * {@code @Model(companyScoped)}, {@code Context.selectedCompanyId}, {@code grantedCompanyIds} — and the
 * HR word stays in the HR models, where {@code Department.legalEntityId} is correctly named.
 *
 * <p><b>Why a table rather than a key in {@link RoleDataScope#getDataScopes()}.</b> That row is keyed
 * by (tenant, role, <i>model</i>) and its {@code scopeExpr} is JSON, so the same list of companies would
 * have to be stored once per company-scoped model — eighteen copies today, updated in lockstep, with
 * nothing detecting a missed one and no error when they disagree. But which companies a role may reach
 * has nothing to do with any particular model: it is a property of the role, so it belongs in a row of
 * its own, next to the other grants. That also buys a plain query for "which roles can reach this
 * company", which JSON cannot answer.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(idStrategy = IdStrategy.DISTRIBUTED_LONG,
        multiTenant = true,
        description = "Role company grant: which companies a role may reach.")
@Index(indexName = "uk_role_company_tenant_role_comp",
        fields = {"tenantId", "roleId", "companyId"}, unique = true,
        message = "This role already has a grant for this company.")
public class RoleCompany extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "Role", required = true, fieldType = FieldType.MANY_TO_ONE,
            relatedModel = Role.class, onDelete = OnDelete.CASCADE,
            description = "Role ID (FK role.id)")
    private Long roleId;

    /**
     * The id of a {@link ModelConstant#COMPANY_MODEL} row.
     *
     * <p><b>Declares no relation</b>, for the same reason {@link RoleNavigation} stores a bare
     * {@code navigationId}: this module cannot name the target. The company model is an HR model, and an
     * app that uses user-starter without HR — the framework's own demo-app does exactly that — has no
     * such model, so a declared {@code relatedModel} would fail its boot outright
     * ({@code ModelManager} rejects a relation whose target is absent from the metadata).
     *
     * <p>The cost is no {@code onDelete = }{@link OnDelete#CASCADE}, so deleting a company leaves its
     * grant rows behind. Those rows are inert rather than dangerous: a dead id inside the grant's
     * {@code IN (…)} matches nothing, and ids are {@link IdStrategy#DISTRIBUTED_LONG} and never reused,
     * so no future company can inherit them. An app that wants them tidied should delete them alongside
     * the company; nothing depends on it having happened.
     */
    @Field(label = "Company", required = true,
            description = "Company ID — the id of a ModelConstant.COMPANY_MODEL row")
    private Long companyId;
}
