package io.softa.starter.file.excel.export;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResolvedTemplateSheet {

    private List<String> headers;

    private List<String> fetchFields;

    private List<String> exportFields;
}
