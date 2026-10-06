package io.softa.starter.permission.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.base.exception.PermissionException;
import io.softa.framework.base.utils.JsonUtils;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.permission.index.EndpointIndex;
import io.softa.starter.permission.scope.ScopeApplicabilityResolver;
import io.softa.starter.permission.scope.ScopeRuleCompiler;
import io.softa.starter.permission.sensitive.SensitiveFieldSetCache;
import io.softa.starter.permission.spi.PermissionInfo;
import io.softa.starter.permission.spi.PermissionSnapshotProvider;
import io.softa.starter.permission.spi.RoleGrant;
import io.softa.starter.permission.spi.ScopeRule;
import io.softa.starter.permission.spi.ScopeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A sensitive field shows on a row when a role that reads the row grants it, and may be written on a
 * row only by a role that both edits the row and grants it.
 *
 * <p>R1 edits a department without IPA, R2 edits the department's non-Employment-Pass holders with
 * IPA, R3 is the all-staff directory. Employee 1 is a Work Permit holder in the department, employee 2
 * an Employment Pass holder in it.
 */
class GrantPairFieldMaskTest {

    private static final String VIEW = "permission.employee.view";
    private static final String UPDATE = "permission.employee.update";
    private static final Set<String> IPA = Set.of("ipaPosition", "ipaBasic");
    private static final String NOT_EP =
            "[[\"employeeProfileId.residenceStatus\",\"!=\",\"SG_EmploymentPass\"]]";

    private PermissionInfo pi;
    private ModelService<Long> modelService;
    private PermissionServiceImpl service;
    private MockedStatic<ModelManager> models;

    @BeforeEach
    void setUp() {
        pi = new PermissionInfo();
        PermissionSnapshotProvider provider = mock(PermissionSnapshotProvider.class);
        when(provider.get(anyLong(), anyLong())).thenAnswer(inv -> pi);

        EndpointIndex index = mock(EndpointIndex.class);
        when(index.lookup(anyString(), anyString())).thenReturn(Set.of());
        when(index.lookup("/Employee/searchPage", "POST")).thenReturn(Set.of(VIEW));
        when(index.lookup("/Employee/updateOne", "POST")).thenReturn(Set.of(UPDATE));

        ScopeApplicabilityResolver applicability = mock(ScopeApplicabilityResolver.class);
        when(applicability.applicableFor("Employee"))
                .thenReturn(Set.of(ScopeType.ALL, ScopeType.CUSTOM, ScopeType.MANAGED_DEPARTMENTS));

        SensitiveFieldSetCache sets = mock(SensitiveFieldSetCache.class);
        when(sets.hasSensitiveFieldsOn("Employee")).thenReturn(true);
        when(sets.allSensitiveFieldsOn("Employee")).thenReturn(IPA);
        when(sets.grantedFieldsFor(eq("Employee"), anySet())).thenAnswer(inv -> {
            Set<String> granted = inv.getArgument(1);
            return granted.contains("ipa-details") ? IPA : Set.of();
        });
        when(sets.computeForbiddenFields(eq("Employee"), anySet())).thenAnswer(inv -> {
            Set<String> granted = inv.getArgument(1);
            return granted.contains("ipa-details") ? Set.of() : IPA;
        });
        when(sets.setIdsContaining(eq("Employee"), anyString())).thenReturn(Set.of("ipa-details"));
        when(sets.nameOf("ipa-details")).thenReturn("IPA Details");

        models = Mockito.mockStatic(ModelManager.class);
        MetaModel meta = mock(MetaModel.class);
        when(meta.getLabel()).thenReturn("Employee");
        models.when(() -> ModelManager.existModel("Employee")).thenReturn(true);
        models.when(() -> ModelManager.getModel("Employee")).thenReturn(meta);

        modelService = mockModelService();
        service = new PermissionServiceImpl(provider, stubCompiler(), sets, modelService, applicability,
                () -> index);
    }

    @AfterEach
    void tearDown() {
        models.close();
    }

    @SuppressWarnings("unchecked")
    private static ModelService<Long> mockModelService() {
        return mock(ModelService.class);
    }

    private static ScopeRuleCompiler stubCompiler() {
        ScopeRuleCompiler compiler = mock(ScopeRuleCompiler.class);
        when(compiler.compile(anyList(), anyString())).thenAnswer(inv -> {
            List<ScopeRule> rules = inv.getArgument(0);
            if (rules.stream().anyMatch(r -> r.getScopeType() == ScopeType.ALL)) return null;
            ScopeRule r = rules.getFirst();
            return r.getScopeType() == ScopeType.CUSTOM
                    ? Filters.of(r.getScopeExpr().toString())
                    : Filters.of("departmentId", Operator.IN, List.of(10L));
        });
        return compiler;
    }

    private static RoleGrant role(long id, Set<String> permissions, ScopeType scope, String condition, boolean ipa) {
        ScopeRule rule = new ScopeRule();
        rule.setScopeType(scope);
        RoleGrant g = new RoleGrant();
        g.setRoleId(id);
        g.setPermissions(new HashSet<>(permissions));
        g.setModelScopeMap(Map.of("Employee", List.of(rule)));
        Map<String, JsonNode> conditions = new HashMap<>();
        if (condition != null) conditions.put("Employee", JsonUtils.stringToObject(condition, JsonNode.class));
        g.setModelScopeConditions(conditions);
        g.setModelSensitiveFieldSetsMap(ipa ? Map.of("Employee", Set.of("ipa-details")) : Map.of());
        return g;
    }

    private void holdTheExampleRoles() {
        pi.setRoleGrants(List.of(
                role(1, Set.of(VIEW, UPDATE), ScopeType.MANAGED_DEPARTMENTS, null, false),
                role(2, Set.of(VIEW, UPDATE), ScopeType.MANAGED_DEPARTMENTS, NOT_EP, true),
                role(3, Set.of(VIEW), ScopeType.ALL, null, false)));
    }

    private static Map<String, Object> row(long id, Object ipaBasic) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("name", "E" + id);
        row.put("ipaBasic", ipaBasic);
        return row;
    }

    private <T> T as(java.util.function.Supplier<T> call) {
        Context ctx = new Context();
        ctx.setTenantId(1L);
        ctx.setUserId(2L);
        return ContextHolder.callWith(ctx, call::get);
    }

    @Test
    @DisplayName("in one list, the Work Permit holder shows IPA and the Employment Pass holder does not")
    void theSameListShowsIpaOnlyOnTheRowsOfTheRoleGrantingIt() {
        holdTheExampleRoles();
        // R2's rows (department AND not EP) hold employee 1 only.
        when(modelService.getIds(eq("Employee"), any(Filters.class))).thenReturn(List.of(1L));
        List<Map<String, Object>> rows = new ArrayList<>(List.of(row(1, 1800), row(2, 6000)));

        as(() -> service.maskResponseValue("Employee", rows, AccessType.READ));

        assertThat(rows.get(0).get("ipaBasic")).isEqualTo(1800);
        assertThat(rows.get(1).get("ipaBasic")).isNull();
        assertThat(rows.get(1).get("name")).isEqualTo("E2");
    }

    @Test
    @DisplayName("a field some reading role grants stays in the SELECT; it is masked per row afterwards")
    void aConditionalFieldIsStillSelected() {
        holdTheExampleRoles();

        assertThat(as(() -> service.filterReadableFields("Employee", List.of("id", "ipaBasic"), AccessType.READ)))
                .containsExactly("id", "ipaBasic");
    }

    @Test
    @DisplayName("a user with no IPA role has the field dropped and masked without any per-row query")
    void withoutAnyGrantingRoleTheFieldIsHiddenEverywhere() {
        pi.setRoleGrants(List.of(role(3, Set.of(VIEW), ScopeType.ALL, null, false)));
        List<Map<String, Object>> rows = new ArrayList<>(List.of(row(1, 1800)));

        assertThat(as(() -> service.filterReadableFields("Employee", List.of("id", "ipaBasic"), AccessType.READ)))
                .containsExactly("id");
        as(() -> service.maskResponseValue("Employee", rows, AccessType.READ));

        assertThat(rows.getFirst().get("ipaBasic")).isNull();
        verify(modelService, never()).getIds(anyString(), any(Filters.class));
    }

    @Test
    @DisplayName("a row without its id shows only what every reading role grants")
    void aRowWithoutAnIdIsMaskedConservatively() {
        holdTheExampleRoles();
        Map<String, Object> row = new HashMap<>();
        row.put("ipaBasic", 1800);

        as(() -> service.maskResponseValue("Employee", List.of(row), AccessType.READ));

        assertThat(row.get("ipaBasic")).isNull();
    }

    @Test
    @DisplayName("writing IPA on the Employment Pass holder is refused with the user's sentence")
    void writingAFieldOutsideTheGrantingEditorsRowsIsRefused() {
        holdTheExampleRoles();
        when(modelService.count(eq("Employee"), any(Filters.class))).thenReturn(0L);

        assertThatThrownBy(() -> as(() -> {
            service.checkIdsFieldsAccess("Employee", List.of(2L), Set.of("ipaBasic"), AccessType.UPDATE);
            return null;
        })).isInstanceOf(PermissionException.class)
                .hasMessage("You don't have permission to edit IPA Details fields for this employee.");
    }

    @Test
    @DisplayName("writing IPA is checked against the rows of the roles that both edit and grant it")
    void theWriteCheckCountsTheGrantingEditorsRows() {
        holdTheExampleRoles();
        when(modelService.count(eq("Employee"), any(Filters.class))).thenReturn(1L);

        as(() -> {
            service.checkIdsFieldsAccess("Employee", List.of(1L), Set.of("ipaBasic"), AccessType.UPDATE);
            return null;
        });

        ArgumentCaptor<Filters> counted = ArgumentCaptor.forClass(Filters.class);
        verify(modelService, Mockito.atLeastOnce()).count(eq("Employee"), counted.capture());
        // The first count is the field's: R2 only — the condition is what tells it apart from R1.
        assertThat(counted.getAllValues().getFirst().toString()).contains("residenceStatus");
    }

    @Test
    @DisplayName("a role that views with IPA but cannot edit does not lend IPA to an editing role")
    void viewingWithIpaPlusEditingWithoutItCannotWriteIpa() {
        pi.setRoleGrants(List.of(
                role(1, Set.of(VIEW, UPDATE), ScopeType.MANAGED_DEPARTMENTS, null, false),
                role(4, Set.of(VIEW), ScopeType.ALL, null, true)));

        assertThatThrownBy(() -> as(() -> {
            service.checkIdsFieldsAccess("Employee", List.of(1L), Set.of("ipaBasic"), AccessType.UPDATE);
            return null;
        })).isInstanceOf(PermissionException.class);
    }

    @Test
    @DisplayName("a condition on IPA matches only on the rows where the caller may see IPA")
    void aConditionOnASensitiveFieldIsBoundToTheGrantingRolesRows() {
        holdTheExampleRoles();

        Filters scoped = as(() -> service.appendScopeAccessFilters("Employee",
                Filters.of("ipaBasic", Operator.GREATER_THAN, 5000)));

        assertThat(scoped.toString()).contains("ipaBasic", "residenceStatus");
    }

    @Test
    @DisplayName("a condition on a field no role grants matches nothing")
    void aConditionOnAnUngrantedFieldMatchesNothing() {
        pi.setRoleGrants(List.of(role(3, Set.of(VIEW), ScopeType.ALL, null, false)));

        Filters scoped = as(() -> service.appendScopeAccessFilters("Employee",
                Filters.of("ipaBasic", Operator.GREATER_THAN, 5000)));

        assertThat(scoped).isEqualTo(ScopeRuleCompiler.matchNone());
    }

    @Test
    @DisplayName("rows read elsewhere — a change log's values — are masked the same way")
    void rowsFromElsewhereAreMaskedByTheirId() {
        holdTheExampleRoles();
        when(modelService.getIds(eq("Employee"), any(Filters.class))).thenReturn(List.of(1L));
        List<Map<String, Object>> rows = new ArrayList<>(List.of(row(1, 1800), row(2, 6000)));

        as(() -> {
            service.maskRows("Employee", rows);
            return null;
        });

        assertThat(rows.get(0).get("ipaBasic")).isEqualTo(1800);
        assertThat(rows.get(1).get("ipaBasic")).isNull();
    }
}
