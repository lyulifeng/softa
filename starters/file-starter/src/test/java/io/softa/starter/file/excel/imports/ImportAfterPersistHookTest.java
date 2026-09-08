package io.softa.starter.file.excel.imports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.utils.SpringContextUtils;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.file.dto.ImportDataDTO;
import io.softa.starter.file.dto.ImportTemplateDTO;
import io.softa.starter.file.enums.ImportRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The point in the pipeline at which "which rows exist now" becomes answerable.
 *
 * <p>Before it there is no answer: the pre-persist handler runs on rows that have not been written,
 * and an import carries no transaction, so a {@code @Transactional} service called from there
 * commits on its own. That is how a partially failed employee import left user accounts behind
 * holding the credentials of employees that were never inserted — and why each retry stranded
 * another batch, since those accounts then failed the next file's duplicate check.
 */
class ImportAfterPersistHookTest {

    /** Records what the hook was handed, and whether it was called at all. */
    private static final class RecordingHandler implements CustomImportHandler {
        private final List<List<Map<String, Object>>> afterPersistCalls = new ArrayList<>();
        private int handleCalls;

        @Override
        public void handleImportData(List<Map<String, Object>> rows, Map<String, Object> env,
                                     boolean validateOnly) {
            handleCalls++;
        }

        @Override
        public void afterPersist(List<Map<String, Object>> rows, Map<String, Object> env) {
            // Copied: the pipeline keeps mutating the live list, and a test asserting on it later
            // would be reading the end state rather than what the hook actually saw.
            afterPersistCalls.add(List.copyOf(rows));
        }
    }

    private final RecordingHandler handler = new RecordingHandler();
    private ImportPersistenceService persistence;

    @BeforeEach
    void bindApplicationContext() {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(eq("recordingHandler"), eq(CustomImportHandler.class))).thenReturn(handler);
        ReflectionTestUtils.setField(SpringContextUtils.class, "applicationContext", context);
    }

    @AfterEach
    void unbindApplicationContext() {
        // The setter refuses to overwrite a bound context, so leaving this behind would hand every
        // later test in this JVM a mock that answers nothing.
        ReflectionTestUtils.setField(SpringContextUtils.class, "applicationContext", null);
    }

    @Test
    void theHookSeesOnlyTheRowsThatLanded() {
        // What persistence does to a partially failed batch: the row that could not be written is
        // taken out of the list. Anything keyed to a row has to run after that, not before.
        droppingTheSecondRowOnPersist();
        ImportDataDTO data = twoRows();

        pipeline().importData(template("recordingHandler"), data);

        assertThat(handler.afterPersistCalls).hasSize(1);
        assertThat(handler.afterPersistCalls.getFirst())
                .as("the failed row is not offered to the hook")
                .singleElement()
                .satisfies(row -> assertThat(row).containsEntry("code", "E001"));
    }

    @Test
    void theHookRunsAfterPersistence_notBeforeIt() {
        // Ordering is the whole point, so assert it rather than trusting the call site: the hook must
        // not be able to see a row before the insert that gives it an id.
        List<String> order = new ArrayList<>();
        persistence = mock(ImportPersistenceService.class);
        doAnswer(invocation -> {
            order.add("persist");
            return null;
        }).when(persistence).persist(any(), any());
        CustomImportHandler ordering = new CustomImportHandler() {
            @Override
            public void handleImportData(List<Map<String, Object>> rows, Map<String, Object> env, boolean v) {
                order.add("handleImportData");
            }

            @Override
            public void afterPersist(List<Map<String, Object>> rows, Map<String, Object> env) {
                order.add("afterPersist");
            }
        };
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(eq("orderingHandler"), eq(CustomImportHandler.class))).thenReturn(ordering);
        ReflectionTestUtils.setField(SpringContextUtils.class, "applicationContext", context);

        pipeline().importData(template("orderingHandler"), twoRows());

        assertThat(order).containsExactly("handleImportData", "persist", "afterPersist");
    }

    @Test
    void aValidationRunNeverReachesTheHook() {
        // A validation run writes nothing, so there is nothing for the hook to act on — and calling it
        // would produce exactly the side effects validate-only exists to withhold.
        persistence = mock(ImportPersistenceService.class);

        pipeline().validateData(template("recordingHandler"), twoRows());

        assertThat(handler.handleCalls).as("the pre-persist handler still runs, for its checks").isOne();
        assertThat(handler.afterPersistCalls).isEmpty();
    }

    @Test
    void nothingLandedMeansNothingToActOn() {
        // Every row failed. The hook's contract promises a non-empty list, so implementations need no
        // guard for the case where the whole file was rejected.
        persistence = mock(ImportPersistenceService.class);
        doAnswer(invocation -> {
            ((ImportDataDTO) invocation.getArgument(1)).getRows().clear();
            return null;
        }).when(persistence).persist(any(), any());

        pipeline().importData(template("recordingHandler"), twoRows());

        assertThat(handler.afterPersistCalls).isEmpty();
    }

    @Test
    void aTemplateWithNoHandlerImportsAsBefore() {
        persistence = mock(ImportPersistenceService.class);

        ImportTemplateDTO template = template(null);
        ImportDataDTO data = twoRows();

        pipeline().importData(template, data);

        assertThat(handler.afterPersistCalls).isEmpty();
        assertThat(data.getRows()).hasSize(2);
    }

    private void droppingTheSecondRowOnPersist() {
        persistence = mock(ImportPersistenceService.class);
        doAnswer(invocation -> {
            ImportDataDTO data = invocation.getArgument(1);
            data.getRows().removeIf(row -> "E002".equals(row.get("code")));
            return null;
        }).when(persistence).persist(any(), any());
    }

    private static ImportTemplateDTO template(String handlerName) {
        ImportTemplateDTO template = new ImportTemplateDTO();
        template.setModelName("Employee");
        template.setImportRule(ImportRule.CREATE_OR_UPDATE);
        template.setUniqueConstraints(List.of("code"));
        template.setSkipException(true);
        template.setCustomHandler(handlerName);
        return template;
    }

    private static ImportDataDTO twoRows() {
        ImportDataDTO data = new ImportDataDTO();
        data.setRows(new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("code", "E001")),
                new LinkedHashMap<>(Map.of("code", "E002")))));
        data.setOriginalRows(new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("Employee Code", "E001")),
                new LinkedHashMap<>(Map.of("Employee Code", "E002")))));
        return data;
    }

    /** The pipeline with everything but the custom-handler wiring stubbed to no-ops. */
    private ImportRowPipeline pipeline() {
        ImportRowPipeline pipeline = new ImportRowPipeline();
        ImportHandlerFactory handlerFactory = mock(ImportHandlerFactory.class);
        when(handlerFactory.createHandlers(any())).thenReturn(List.of());
        RelationLookupResolver lookupResolver = mock(RelationLookupResolver.class);
        when(lookupResolver.detectLookupGroups(any(), any())).thenReturn(List.of());
        UniqueConstraintValidator validator = new UniqueConstraintValidator();
        ModelService<?> modelService = mock(ModelService.class);
        when(modelService.searchList(anyString(), any())).thenReturn(List.of());
        ReflectionTestUtils.setField(validator, "modelService", modelService);

        ReflectionTestUtils.setField(pipeline, "importHandlerFactory", handlerFactory);
        ReflectionTestUtils.setField(pipeline, "relationLookupResolver", lookupResolver);
        ReflectionTestUtils.setField(pipeline, "uniqueConstraintValidator", validator);
        ReflectionTestUtils.setField(pipeline, "importFailureCollector", new ImportFailureCollector());
        ReflectionTestUtils.setField(pipeline, "importPersistenceService", persistence);
        return pipeline;
    }
}
