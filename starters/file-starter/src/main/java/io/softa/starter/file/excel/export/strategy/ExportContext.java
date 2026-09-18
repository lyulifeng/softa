package io.softa.starter.file.excel.export.strategy;

import lombok.Getter;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.starter.file.entity.ExportTemplate;
import io.softa.starter.file.vo.PivotSpec;

/**
 * Export context used by export strategies.
 */
@Getter
public class ExportContext {

    private final ExportMode exportMode;

    private final String modelName;

    private final ExportTemplate exportTemplate;

    private final FlexQuery flexQuery;

    /** Pivot columns to append on a dynamic export; null for none. See {@link PivotSpec}. */
    private final PivotSpec pivot;

    private ExportContext(ExportMode exportMode, String modelName, ExportTemplate exportTemplate, FlexQuery flexQuery,
                          PivotSpec pivot) {
        this.exportMode = exportMode;
        this.modelName = modelName;
        this.exportTemplate = exportTemplate;
        this.flexQuery = flexQuery;
        this.pivot = pivot;
    }

    public static ExportContext dynamic(String modelName, FlexQuery flexQuery) {
        return dynamic(modelName, flexQuery, null);
    }

    public static ExportContext dynamic(String modelName, FlexQuery flexQuery, PivotSpec pivot) {
        return new ExportContext(ExportMode.DYNAMIC, modelName, null, flexQuery, pivot);
    }

    public static ExportContext template(ExportTemplate exportTemplate, FlexQuery flexQuery) {
        ExportMode exportMode = Boolean.TRUE.equals(exportTemplate.getCustomFileTemplate())
                ? ExportMode.FILE_TEMPLATE
                : ExportMode.FIELD_TEMPLATE;
        return new ExportContext(exportMode, null, exportTemplate, flexQuery, null);
    }

}
