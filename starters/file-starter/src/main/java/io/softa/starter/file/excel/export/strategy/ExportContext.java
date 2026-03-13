package io.softa.starter.file.excel.export.strategy;

import lombok.Getter;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.starter.file.entity.ExportTemplate;

/**
 * Export context used by export strategies.
 */
@Getter
public class ExportContext {

    private final ExportMode exportMode;

    private final String modelName;

    private final ExportTemplate exportTemplate;

    private final FlexQuery flexQuery;

    private ExportContext(ExportMode exportMode, String modelName, ExportTemplate exportTemplate, FlexQuery flexQuery) {
        this.exportMode = exportMode;
        this.modelName = modelName;
        this.exportTemplate = exportTemplate;
        this.flexQuery = flexQuery;
    }

    public static ExportContext dynamic(String modelName, FlexQuery flexQuery) {
        return new ExportContext(ExportMode.DYNAMIC, modelName, null, flexQuery);
    }

    public static ExportContext template(ExportTemplate exportTemplate, FlexQuery flexQuery) {
        ExportMode exportMode = Boolean.TRUE.equals(exportTemplate.getCustomFileTemplate())
                ? ExportMode.FILE_TEMPLATE
                : ExportMode.FIELD_TEMPLATE;
        return new ExportContext(exportMode, null, exportTemplate, flexQuery);
    }

}
