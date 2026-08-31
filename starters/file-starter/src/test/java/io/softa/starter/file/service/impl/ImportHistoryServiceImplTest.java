package io.softa.starter.file.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which imports a page shows.
 *
 * <p>The template list a page offers is the model's own plus its CHILD models' — that is how one
 * employee page hands out the templates for addresses, family members and the rest. The history read
 * the model alone, so an import started from that page finished and then appeared nowhere on it: the
 * file uploaded, the rows landed, the list stayed empty, and the only way to see the run was a SQL
 * client.
 */
class ImportHistoryServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void theHistoryCoversTheSameModelsTheTemplateListOffers() {
        try (var models = org.mockito.Mockito.mockStatic(ModelManager.class)) {
            models.when(() -> ModelManager.getChildModels("Employee"))
                    .thenReturn(Set.of("EmpAddress", "EmpFamilyMember"));
            models.when(() -> ModelManager.getModel(anyString())).thenReturn(new MetaModel());

            ModelService<Long> modelService = mock(ModelService.class);
            when(modelService.searchList(anyString(), any(FlexQuery.class))).thenReturn(List.of());

            ImportHistoryServiceImpl service = new ImportHistoryServiceImpl();
            org.springframework.test.util.ReflectionTestUtils.setField(service, "modelService", modelService);
            org.springframework.test.util.ReflectionTestUtils.setField(service, "modelName", "ImportHistory");

            Context context = new Context();
            context.setUserId(7L);
            ContextHolder.runWith(context, () -> service.listMyImportHistory("Employee"));

            ArgumentCaptor<FlexQuery> query = ArgumentCaptor.forClass(FlexQuery.class);
            verify(modelService).searchList(anyString(), query.capture());

            String filters = String.valueOf(query.getValue().getFilters());
            assertThat(filters)
                    .as("a child model's import must be visible where its template was offered")
                    .contains("EmpAddress")
                    .contains("EmpFamilyMember")
                    .contains("Employee");
        }
    }
}
