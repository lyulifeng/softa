package io.softa.starter.file.excel.export.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.starter.file.excel.export.support.ExportDataFetcher;
import io.softa.starter.file.excel.export.support.PivotColumns;
import io.softa.starter.file.vo.PivotSpec;

/** The pivot's columns follow the picked fields on the path the single- and multi-sheet exports share. */
class ExportByDynamicPivotTest {

    @Test
    void thePivotColumnsFollowThePickedFields() {
        ExportByDynamic strategy = new ExportByDynamic();
        ExportDataFetcher fetcher = mock(ExportDataFetcher.class);
        PivotColumns pivot = mock(PivotColumns.class);
        ReflectionTestUtils.setField(strategy, "exportDataFetcher", fetcher);
        ReflectionTestUtils.setField(strategy, "pivotColumns", pivot);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("deptId", "HQ");
        when(fetcher.fetchRows(eq("DeptFteStats"), isNull(), any(FlexQuery.class))).thenReturn(List.of(row));
        doAnswer(invocation -> {
            List<String> headers = invocation.getArgument(3);
            List<List<Object>> table = invocation.getArgument(4);
            headers.add("Full Time (SG)");
            table.get(0).add(8);
            return null;
        }).when(pivot).append(eq("DeptFteStats"), any(PivotSpec.class), any(), any(), any());
        FlexQuery query = new FlexQuery();
        query.setFields(List.of("deptId"));
        List<String> headers = new ArrayList<>();

        List<List<Object>> table;
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            MetaField dept = new MetaField();
            ReflectionTestUtils.setField(dept, "label", "Department");
            mm.when(() -> ModelManager.getLastFieldOfCascaded("DeptFteStats", "deptId")).thenReturn(dept);
            table = strategy.extractDataTableWithPivot("DeptFteStats", query, new PivotSpec(), headers);
        }

        assertThat(headers).containsExactly("Department", "Full Time (SG)");
        assertThat(table.get(0)).containsExactly("HQ", 8);
    }
}
