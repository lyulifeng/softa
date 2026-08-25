package io.softa.starter.file.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.dto.FileInfo;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.file.service.ExportService;
import io.softa.starter.file.dto.SheetInfo;
import io.softa.starter.file.vo.ExportParams;
import io.softa.starter.file.vo.MultiSheetExportParams;

/**
 * ExportController
 */
@Tag(name = "Data Export")
@RestController
@RequestMapping("/export")
public class ExportController {

    @Autowired
    private ExportService exportService;

    /**
     * Export data by dynamic fields and ExportParams, without export template.
     * The convertType is set to DISPLAY to get the display values of the fields.
     * Such as displayName for ManyToOne/OneToOne fields, and label for Option fields.
     *
     * @param modelName the model name to be exported
     * @param exportParams the export parameters of the data to be exported
     * @return fileInfo object with download URL
     */
    @Operation(description = "Export data by dynamic fields and ExportParams, without export template.")
    @PostMapping(value = "/dynamicExport")
    public ApiResponse<FileInfo> dynamicExport(@RequestParam String modelName,
                                               @RequestBody ExportParams exportParams) {
        FlexQuery flexQuery = ExportParams.convertParamsToFlexQuery(exportParams);
        return ApiResponse.success(exportService.dynamicExport(modelName, flexQuery));
    }

    /**
     * Export data by exportTemplate configured exported fields or a custom file template.
     * The custom file template is a template file that contains the variables to be filled in.
     * The convertType is set to DISPLAY to get the display values of the fields.
     * Such as displayName for ManyToOne/OneToOne fields, and label for Option fields.
     *
     * @param exportTemplateId The ID of the export template
     * @param exportParams the export parameters of the data to be exported
     * @return fileInfo object with download URL
     */
    @Operation(description = "Export data by exportTemplate configured exported fields or a custom file template.")
    @PostMapping(value = "/exportByTemplate")
    @Parameter(name = "exportTemplateId", description = "The id of the ExportTemplate.")
    public ApiResponse<FileInfo> exportByTemplate(@RequestParam Long exportTemplateId,
                                                  @RequestBody ExportParams exportParams) {
        FlexQuery flexQuery = ExportParams.convertParamsToFlexQuery(exportParams);
        return ApiResponse.success(exportService.exportByTemplate(exportTemplateId, flexQuery));
    }

    /**
     * Export several objects into one workbook, a sheet each.
     *
     * <p>Exists because the objects hanging off a record are separate models: an employee's addresses,
     * family members and contacts are three queries, and asking for "this employee and everything
     * under them" means three sheets in one file rather than three downloads.
     *
     * <p>Each sheet should include its object's {@code code}, which is what an edited sheet is fed
     * back in by — a code updates the row it names, a blank one creates a new one.
     *
     * @param multiSheetExportParams the file name and one entry per object
     * @return fileInfo object with download URL
     */
    @Operation(description = "Export several models into one workbook, one sheet each.")
    @PostMapping(value = "/dynamicExportMultiSheet")
    public ApiResponse<FileInfo> dynamicExportMultiSheet(
            @RequestBody MultiSheetExportParams multiSheetExportParams) {
        List<SheetInfo> sheetInfoList = new ArrayList<>();
        for (MultiSheetExportParams.Sheet sheet : multiSheetExportParams.getSheets()) {
            SheetInfo sheetInfo = new SheetInfo();
            sheetInfo.setModelName(sheet.getModelName());
            sheetInfo.setSheetName(sheet.getSheetName());
            sheetInfo.setFlexQuery(ExportParams.convertParamsToFlexQuery(sheet.getExportParams()));
            sheetInfoList.add(sheetInfo);
        }
        return ApiResponse.success(exportService.dynamicExportMultiSheet(
                multiSheetExportParams.getFileName(), sheetInfoList));
    }

}
