package io.softa.starter.es.service.impl;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.changelog.message.dto.ChangeLog;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.domain.Page;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.service.PermissionService;
import io.softa.starter.es.document.ChangeLogDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A model-level log read is bounded by the caller's tenant and by the rows of the model the
 * caller may read — the two conditions that stand where a role gate used to.
 */
class ChangeLogSearchScopeTest {

    private static final long TENANT = 7L;

    private ChangeLogServiceImpl service;
    private PermissionService permissionService;
    private ModelService<Serializable> modelService;
    /** The filters the Elasticsearch read was finally asked with. */
    private final AtomicReference<Filters> asked = new AtomicReference<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = spy(new ChangeLogServiceImpl());
        permissionService = mock(PermissionService.class);
        modelService = mock(ModelService.class);
        ReflectionTestUtils.setField(service, "permissionService", permissionService);
        ReflectionTestUtils.setField(service, "modelService", modelService);
        doAnswer(inv -> {
            asked.set(inv.getArgument(1));
            Page<ChangeLogDocument> page = inv.getArgument(3);
            page.setRows(List.of());
            page.setTotalCount(0);
            return page;
        }).when(service).searchPage(eq(ChangeLogDocument.class), any(), any(), any());
    }

    private void inTenant(Runnable action) {
        Context ctx = new Context();
        ctx.setTenantId(TENANT);
        ContextHolder.runWith(ctx, action);
    }

    private Page<ChangeLog> search(String model) {
        return service.searchPageByModel(model, new FlexQuery(new Filters()), Page.of(1, 20));
    }

    @Test
    void everyReadIsConfinedToTheCallersTenant() {
        when(permissionService.appendScopeAccessFilters(eq("Employee"), any())).thenReturn(new Filters());
        inTenant(() -> search("Employee"));
        assertThat(asked.get().toString()).contains("\"tenantId\"").contains(String.valueOf(TENANT));
    }

    @Test
    void anUnrestrictedCallerSeesTheWholeTenantLogOfTheModel() {
        when(permissionService.appendScopeAccessFilters(eq("Employee"), any())).thenReturn(new Filters());
        inTenant(() -> search("Employee"));
        assertThat(asked.get().toString()).doesNotContain("\"rowId\"");
        verify(modelService, never()).getIds(any(), any());
    }

    @Test
    void aScopedCallerSeesOnlyTheRowsInsideTheirScope() {
        when(permissionService.appendScopeAccessFilters(eq("Employee"), any()))
                .thenReturn(new Filters().eq("departmentId", 3L));
        when(modelService.getIds(eq("Employee"), any())).thenReturn(List.of(101L, 102L));
        inTenant(() -> search("Employee"));
        assertThat(asked.get().toString()).contains("\"rowId\"").contains("101").contains("102");
    }

    @Test
    void aCallerWithNoReadableRowSeesNothingAndNoReadIsMade() {
        when(permissionService.appendScopeAccessFilters(eq("Employee"), any()))
                .thenReturn(new Filters().eq("departmentId", 3L));
        when(modelService.getIds(eq("Employee"), any())).thenReturn(List.of());
        AtomicReference<Page<ChangeLog>> result = new AtomicReference<>();
        inTenant(() -> result.set(search("Employee")));
        assertThat(result.get().getRows()).isEmpty();
        assertThat(result.get().getTotalCount()).isZero();
        verify(service, never()).searchPage(eq(ChangeLogDocument.class), any(), any(), any());
    }

    @Test
    void aRowReadIsConfinedToTheTenantToo() {
        inTenant(() -> service.getRowChangeLog("Employee", 101L, Page.of(1, 20), Orders.DESC, false));
        assertThat(asked.get().toString()).contains("\"tenantId\"").contains("\"rowId\"");
    }

    @Test
    void withoutATenantNothingIsAdded() {
        when(permissionService.appendScopeAccessFilters(eq("Employee"), any())).thenReturn(new Filters());
        ContextHolder.runWith(new Context(), () -> search("Employee"));
        assertThat(asked.get().toString()).doesNotContain("\"tenantId\"");
    }
}
