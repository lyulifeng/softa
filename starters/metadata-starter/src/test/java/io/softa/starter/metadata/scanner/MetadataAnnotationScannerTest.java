package io.softa.starter.metadata.scanner;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import io.softa.framework.base.config.SystemConfig;
import io.softa.starter.metadata.config.MetadataProperties;
import io.softa.starter.metadata.ddl.DdlOrchestrator;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema;
import io.softa.starter.metadata.entity.SysModel;
import io.softa.starter.metadata.scanner.annotation.AnnotationScanResult;
import io.softa.starter.metadata.scanner.diff.SchemaDiff;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Verifies the safety properties of {@link MetadataAnnotationScanner#initialize()}:
 * <ul>
 *   <li>prod default: with an empty {@code scanner-scope} the scanner is an
 *       inert no-op — it never reads or writes the database;</li>
 *   <li>recovery ordering: DDL executes <b>before</b> the {@code sys_*} rows
 *       are committed — on the convergence lane (physical facts available) and on
 *       the degraded metadata-only lane (introspection failed) alike — so a DDL
 *       failure leaves the rows unwritten and the next boot retries;</li>
 *   <li>drift healing is not gated on a metadata diff: with facts available the
 *       convergence pass runs even on an idempotent catalog.</li>
 * </ul>
 */
class MetadataAnnotationScannerTest {

    @Test
    void emptyScopeIsAnInertNoOp() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // SysJdbcWriter's constructor wires a transaction manager from the
        // DataSource, so the mock must return one during construction.
        when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));

        MetadataReadPipeline pipeline = mock(MetadataReadPipeline.class);
        SystemConfig systemConfig = new SystemConfig();
        systemConfig.setAppCode("test-app");
        MetadataAnnotationScanner scanner = new MetadataAnnotationScanner(
                pipeline,
                new MetadataProperties(List.of(), null, null),   // empty scope
                systemConfig,
                jdbc,
                "jdbc:mysql://localhost/unused",
                "");   // no PostgreSQL string collation

        // Ignore construction-time interactions (getDataSource); assert that
        // initialize() itself touches neither the DB nor the read pipeline.
        clearInvocations(jdbc);
        scanner.initialize();
        verifyNoInteractions(jdbc);
        verifyNoInteractions(pipeline);
    }

    // ---- fixtures ---------------------------------------------------------

    private record Fixture(MetadataAnnotationScanner scanner,
                           SysJdbcWriter writer,
                           DdlOrchestrator ddl,
                           SchemaDiff diff) {}

    /** Wire a scanner whose pipeline yields one added model. */
    private Fixture scannerWithOneAddedModel() {
        SysModel customer = new SysModel();
        customer.setModelName("Customer");
        SchemaDiff diff = new SchemaDiff(
                new SchemaDiff.EntityDiff<>(List.of(customer), List.of(), List.of()),
                SchemaDiff.EntityDiff.empty(),
                SchemaDiff.EntityDiff.empty(),
                SchemaDiff.EntityDiff.empty(),
                SchemaDiff.EntityDiff.empty());
        return scannerReturningDiff(diff);
    }

    /** Wire a scanner whose pipeline yields one in-scope model and returns {@code diff} from the diff step. */
    private Fixture scannerReturningDiff(SchemaDiff diff) {
        MetadataReadPipeline pipeline = mock(MetadataReadPipeline.class);
        SysJdbcWriter writer = mock(SysJdbcWriter.class);
        DdlOrchestrator ddl = mock(DdlOrchestrator.class);

        SysModel customer = new SysModel();
        customer.setModelName("Customer");
        AnnotationScanResult fromCode = new AnnotationScanResult(
                List.of(customer), List.of(), List.of(), List.of());

        when(pipeline.discoverModelClasses()).thenReturn(Set.of(Object.class));
        when(pipeline.discoverOptionSetEnums()).thenReturn(Set.of());
        when(pipeline.parse(anyCollection(), anyCollection()))
                .thenReturn(fromCode)                       // in-scope model parse
                .thenReturn(AnnotationScanResult.empty());  // option-set parse
        when(pipeline.loadCurrentState()).thenReturn(AnnotationScanResult.empty());
        when(pipeline.diff(any(), any())).thenReturn(diff);
        when(writer.changeSummary(diff)).thenReturn(List.of());

        MetadataAnnotationScanner scanner = new MetadataAnnotationScanner(
                pipeline, new MetadataProperties(List.of("*"), null, null), "test-app", writer, ddl);
        return new Fixture(scanner, writer, ddl, diff);
    }

    /** Physical introspection succeeds — the scanner must take the convergence lane. */
    private static void withFacts(Fixture f) {
        when(f.ddl().introspect(anyList())).thenReturn(new PhysicalSchema(Map.of()));
    }

    // ---- convergence lane: DDL-before-rows ordering -------------------------

    @Test
    void convergeExecutesBeforeSysRowsAreCommitted() {
        Fixture f = scannerWithOneAddedModel();
        withFacts(f);

        f.scanner().initialize();

        InOrder inOrder = inOrder(f.ddl(), f.writer());
        inOrder.verify(f.ddl()).converge(anyList(), anyList(), anyList(), eq(f.diff()), any());
        inOrder.verify(f.writer()).apply(f.diff());
        // With facts available the metadata-only fallback must not also run.
        verify(f.ddl(), never()).apply(any(), anyList(), anyList());
    }

    @Test
    void convergeFailureLeavesSysRowsUnwritten() {
        Fixture f = scannerWithOneAddedModel();
        withFacts(f);
        doThrow(new IllegalStateException("ALTER TABLE failed"))
                .when(f.ddl()).converge(anyList(), anyList(), anyList(), any(), any());

        assertThrows(IllegalStateException.class, f.scanner()::initialize);

        // The recovery property: no sys_* row may be written after a DDL
        // failure, so the next boot recomputes the same diff and retries.
        verify(f.writer(), never()).apply(any(SchemaDiff.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshotUniverseIncludesPriorTablesOfPendingRenames() {
        // The convergence planner reads the rename verb from the physical facts, so the
        // snapshot must cover the table a rename-in-flight is still physically under —
        // otherwise the planner would see "new table missing" and genesis-create an empty
        // successor (silent data divorce) instead of renaming.
        SysModel before = new SysModel();
        before.setModelName("OldCustomer");
        before.setTableName("old_customer");
        SysModel after = new SysModel();
        after.setModelName("Customer");
        SchemaDiff diff = new SchemaDiff(
                new SchemaDiff.EntityDiff<>(List.of(), List.of(),
                        List.of(new SchemaDiff.Modification<>(after, before, SchemaDiff.Kind.RENAME))),
                SchemaDiff.EntityDiff.empty(),
                SchemaDiff.EntityDiff.empty(),
                SchemaDiff.EntityDiff.empty(),
                SchemaDiff.EntityDiff.empty());
        Fixture f = scannerReturningDiff(diff);
        withFacts(f);

        f.scanner().initialize();

        ArgumentCaptor<List<SysModel>> universe = ArgumentCaptor.forClass(List.class);
        verify(f.ddl(), atLeastOnce()).introspect(universe.capture());
        assertTrue(universe.getAllValues().stream().flatMap(List::stream)
                        .anyMatch(m -> "old_customer".equals(m.getTableName())),
                "the snapshot universe must include the rename's prior table");
    }

    @Test
    void driftConvergenceRunsEvenOnAnEmptyDiff() {
        // An idempotent catalog can still hide hand-made physical drift — the convergence
        // pass is gated on the facts, never on the diff.
        Fixture f = scannerReturningDiff(SchemaDiff.empty());
        withFacts(f);

        f.scanner().initialize();

        verify(f.ddl()).converge(anyList(), anyList(), anyList(), eq(f.diff()), any());
        verify(f.writer(), never()).apply(any(SchemaDiff.class));
    }

    // ---- degraded lane (introspection failed): metadata-only DDL ------------

    @Test
    void withoutFacts_ddlExecutesBeforeSysRowsAreCommitted() {
        Fixture f = scannerWithOneAddedModel();   // introspect() mock defaults to null

        f.scanner().initialize();

        InOrder inOrder = inOrder(f.ddl(), f.writer());
        inOrder.verify(f.ddl()).apply(eq(f.diff()), anyList(), anyList());
        inOrder.verify(f.writer()).apply(f.diff());
        verify(f.ddl(), never()).converge(anyList(), anyList(), anyList(), any(), any());
    }

    @Test
    void withoutFacts_ddlFailureLeavesSysRowsUnwritten() {
        Fixture f = scannerWithOneAddedModel();
        doThrow(new IllegalStateException("CREATE TABLE failed"))
                .when(f.ddl()).apply(any(), anyList(), anyList());

        assertThrows(IllegalStateException.class, f.scanner()::initialize);

        verify(f.writer(), never()).apply(any(SchemaDiff.class));
    }

    // ---- finalization: app_code stamp then surrogate-FK resolution --------

    @Test
    void finalizationStampsAppCodeThenResolvesFks() {
        Fixture f = scannerWithOneAddedModel();

        f.scanner().initialize();

        // populateSurrogateFks joins on the stamped app_code, so it must run AFTER backfillAppCode —
        // which itself runs after the rows are applied.
        InOrder inOrder = inOrder(f.writer());
        inOrder.verify(f.writer()).apply(f.diff());
        inOrder.verify(f.writer()).backfillAppCode();
        inOrder.verify(f.writer()).populateSurrogateFks();
    }

    @Test
    void emptyDiffStillRunsFinalizationSoAPartialBootSelfHeals() {
        Fixture f = scannerReturningDiff(SchemaDiff.empty());

        f.scanner().initialize();

        // Steady-state boot without facts: an empty diff applies no DDL and no rows...
        verify(f.ddl(), never()).apply(any(), anyList(), anyList());
        verify(f.writer(), never()).apply(any(SchemaDiff.class));
        // ...but backfill + FK resolution are unconditional, so a row left NULL / unlinked by a prior
        // partial boot self-heals on the next (idempotent) restart.
        verify(f.writer()).backfillAppCode();
        verify(f.writer()).populateSurrogateFks();
    }
}
