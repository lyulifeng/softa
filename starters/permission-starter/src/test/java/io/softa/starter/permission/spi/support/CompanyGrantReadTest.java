package io.softa.starter.permission.spi.support;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reading the role → company grant into the permission snapshot.
 *
 * <p>Worth its own class because of how this fails. The grant is read <b>by name</b> — this module
 * cannot see {@code RoleCompany}, which lives in user-starter — so nothing links the two at compile
 * time. Rename the model or its column and the query returns nothing; an empty grant means
 * <b>unrestricted</b>, so the result is every role reaching every company, silently, with a green
 * build. That exact rename happened once already ({@code RoleLegalEntity} → {@code RoleCompany},
 * {@code legalEntityId} → {@code companyId}). These assertions are the only thing standing between the
 * next one and a quiet loss of enforcement.
 */
class CompanyGrantReadTest {

    private static final String ROLE_COMPANY = "RoleCompany";
    private static final List<Long> ROLE_IDS = List.of(11L, 22L);

    private MockedStatic<ModelManager> modelManager;
    private ModelService<Long> modelService;
    private DefaultPermissionSnapshotProvider provider;

    @BeforeEach
    void setUp() {
        modelManager = Mockito.mockStatic(ModelManager.class);
        modelService = mockModelService();
        provider = new DefaultPermissionSnapshotProvider(null, modelService, null, List.of());
    }

    @SuppressWarnings("unchecked")
    private static ModelService<Long> mockModelService() {
        return mock(ModelService.class);
    }

    @AfterEach
    void tearDown() {
        modelManager.close();
    }

    private void grantRows(List<Map<String, Object>> rows) {
        modelManager.when(() -> ModelManager.existModel(ROLE_COMPANY)).thenReturn(true);
        when(modelService.searchList(eq(ROLE_COMPANY), any(FlexQuery.class))).thenReturn(rows);
    }

    @Test
    void readsTheGrantFromRoleCompanyDotCompanyId() {
        // The two names this module hard-codes. Asserted on the query itself, not just the result, so a
        // rename fails here rather than in production as "everyone can see everything".
        grantRows(List.of(Map.of("companyId", 8712L), Map.of("companyId", 9001L)));

        Set<Long> granted = provider.readGrantedCompanyIds(ROLE_IDS);

        assertThat(granted).containsExactlyInAnyOrder(8712L, 9001L);
        ArgumentCaptor<FlexQuery> captor = ArgumentCaptor.forClass(FlexQuery.class);
        verify(modelService).searchList(eq(ROLE_COMPANY), captor.capture());
        FlexQuery query = captor.getValue();
        assertThat(query.getFields()).containsExactly("companyId");
        assertThat(query.getFilters().toString()).contains("roleId", "11", "22");
    }

    @Test
    void unionsTheGrantsOfEveryRoleAndDeduplicates() {
        // Two roles granting the same company is ordinary, not a data error.
        grantRows(List.of(Map.of("companyId", 8712L), Map.of("companyId", 8712L), Map.of("companyId", 9001L)));

        assertThat(provider.readGrantedCompanyIds(ROLE_IDS)).containsExactlyInAnyOrder(8712L, 9001L);
    }

    @Test
    void skipsRowsWhoseCompanyIdIsNotANumber() {
        // A null column or a value of an unexpected shape must not become a grant entry — silently
        // dropping one company is bad, but a malformed entry that matches nothing would empty the
        // caller's screens instead, which reads as an outage.
        grantRows(java.util.Arrays.asList(
                java.util.Collections.singletonMap("companyId", null),
                Map.of("companyId", "not-a-number"),
                Map.of("companyId", 8712L)));

        assertThat(provider.readGrantedCompanyIds(ROLE_IDS)).containsExactly(8712L);
    }

    @Test
    void anAbsentModelCostsNeitherAQueryNorAnException() {
        // An application without a company dimension — the framework's own demo apps. Same degradation
        // as SelectedCompanyCountryEnricher: no model, no narrowing, no noise.
        modelManager.when(() -> ModelManager.existModel(ROLE_COMPANY)).thenReturn(false);

        assertThat(provider.readGrantedCompanyIds(ROLE_IDS)).isEmpty();
        verify(modelService, never()).searchList(eq(ROLE_COMPANY), any(FlexQuery.class));
    }

    @Test
    void noGrantRowsMeansAnEmptySetRatherThanNull() {
        // PermissionServiceImpl reads "empty" as unrestricted; a null there would NPE on the hot path.
        grantRows(List.of());

        assertThat(provider.readGrantedCompanyIds(ROLE_IDS)).isNotNull().isEmpty();
    }
}
