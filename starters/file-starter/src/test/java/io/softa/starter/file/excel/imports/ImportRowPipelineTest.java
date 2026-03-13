package io.softa.starter.file.excel.imports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.exception.IllegalArgumentException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportRowPipelineTest {

    @Test
    void validateCustomHandlerContractAllowsInPlaceMutation() {
        ImportRowPipeline importRowPipeline = new ImportRowPipeline();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("name", "A")));
        rows.add(new LinkedHashMap<>(Map.of("name", "B")));
        List<Integer> snapshot = rows.stream().map(System::identityHashCode).toList();

        rows.getFirst().put("name", "Updated");

        assertDoesNotThrow(() -> importRowPipeline.validateCustomHandlerContract("handler", rows, 2, snapshot));
    }

    @Test
    void validateCustomHandlerContractRejectsRowReordering() {
        ImportRowPipeline importRowPipeline = new ImportRowPipeline();
        Map<String, Object> rowA = new LinkedHashMap<>(Map.of("name", "A"));
        Map<String, Object> rowB = new LinkedHashMap<>(Map.of("name", "B"));
        List<Map<String, Object>> rows = new ArrayList<>(List.of(rowA, rowB));
        List<Integer> snapshot = rows.stream().map(System::identityHashCode).toList();

        rows.set(0, rowB);
        rows.set(1, rowA);

        assertThrows(IllegalArgumentException.class,
                () -> importRowPipeline.validateCustomHandlerContract("handler", rows, 2, snapshot));
    }

    @Test
    void validateCustomHandlerContractRejectsRowCountChanges() {
        ImportRowPipeline importRowPipeline = new ImportRowPipeline();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("name", "A")));
        List<Integer> snapshot = rows.stream().map(System::identityHashCode).toList();

        rows.add(new LinkedHashMap<>(Map.of("name", "B")));

        assertThrows(IllegalArgumentException.class,
                () -> importRowPipeline.validateCustomHandlerContract("handler", rows, 1, snapshot));
    }
}
