package io.softa.starter.file.excel.export.strategy;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.softa.framework.base.exception.IllegalArgumentException;

@Component
public class ExportStrategyFactory {

    @Autowired
    private List<ExportStrategy> exportStrategies;

    public ExportStrategy getStrategy(ExportContext exportContext) {
        return getStrategy(exportContext.getExportMode());
    }

    public ExportStrategy getStrategy(ExportMode exportMode) {
        return exportStrategies.stream()
                .filter(strategy -> strategy.getMode() == exportMode)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No export strategy matches export mode `{0}`.",
                        exportMode));
    }
}
