package io.softa.starter.metadata.service.impl;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataServiceImplTest {

    @Test
    void exportRuntimeMetadataUsesRequestedModelName() {
        MetadataServiceImpl service = new MetadataServiceImpl();
        @SuppressWarnings("unchecked")
        ModelService<Serializable> modelService = mock(ModelService.class);
        ReflectionTestUtils.setField(service, "modelService", modelService);

        List<Map<String, Object>> rows = List.of(Map.of("id", 1L, "fieldName", "name"));
        when(modelService.searchList(eq("SysField"), Mockito.any(FlexQuery.class))).thenReturn(rows);

        try (MockedStatic<ModelManager> modelManager = Mockito.mockStatic(ModelManager.class)) {
            modelManager.when(() -> ModelManager.getModelFieldsWithoutXToMany("SysField"))
                    .thenReturn(Set.of("id", "fieldName"));

            List<Map<String, Object>> result = service.exportRuntimeMetadata("SysField");

            assertEquals(rows, result);
            verify(modelService).searchList(eq("SysField"), Mockito.argThat(query ->
                    query != null
                            && query.getFields().size() == 2
                            && query.getFields().containsAll(Set.of("id", "fieldName"))));
        }
    }
}
