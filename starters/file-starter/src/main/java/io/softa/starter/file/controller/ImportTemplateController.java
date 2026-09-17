package io.softa.starter.file.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.apache.commons.lang3.StringUtils;

import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.scope.MultiCountryScope;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.dto.FileInfo;
import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.file.entity.ImportTemplate;
import io.softa.starter.file.support.TemplateScope;
import io.softa.starter.file.service.ImportService;
import io.softa.starter.file.service.ImportTemplateService;

/**
 * ImportTemplateController
 */
@Tag(name = "Import Template")
@RestController
@RequestMapping("/ImportTemplate")
public class ImportTemplateController extends EntityController<ImportTemplateService, ImportTemplate, Long> {

    @Autowired
    private ImportService importService;

    /**
     * List all import templates of the specified model
     *
     * @param modelName model name
     * @return list of import templates
     */
    @Operation(summary="listByModel", description = "List all import templates of the specified model")
    @PostMapping(value = "/listByModel")
    public ApiResponse<List<ImportTemplate>> listByModel(@RequestParam String modelName) {
        Set<String> modelNames = TemplateScope.of(modelName, this::standaloneModelNames);
        Filters filters = new Filters().in(ImportTemplate::getModelName, modelNames);
        Filters countryScope = countryScope();
        if (countryScope != null) {
            filters.and(countryScope);
        }
        FlexQuery flexQuery = new FlexQuery(filters).expandSubQuery(ImportTemplate::getImportFields);
        List<ImportTemplate> templates = service.searchList(flexQuery);
        return ApiResponse.success(templates);
    }

    /**
     * Narrows the listing to the countries the caller works in, or returns null to leave it alone.
     *
     * <p>Written as <b>country is null OR country in (my countries)</b>, never a bare membership test:
     * a template with no country applies to all of them, and that is the overwhelming majority — the
     * ones with a country are the exception (employee and legal-entity templates), and every row holds
     * null on the release that adds the column. A bare membership test would empty the dialog for every
     * tenant.
     *
     * <p>The set is the caller's own countries — those of the companies their roles reach — the same
     * set {@code MultiCountryScope} narrows value domains by, resolved the same way (falling back to the
     * country of the company the caller belongs to). When neither is known the filter is skipped rather
     * than tightened: showing every template beats showing none.
     *
     * <p>Resolved server-side from the request context and never read off the payload.
     */
    Filters countryScope() {
        List<String> countries = MultiCountryScope.countriesInPlay(ContextHolder.getContext());
        if (countries.isEmpty()) {
            return null;
        }
        return new Filters().add(ImportTemplate::getCountry, Operator.IS_NOT_SET, null)
                .or(new Filters().in(ImportTemplate::getCountry, countries));
    }
    /**
     * Get the fileInfo of the import template by template ID.
     * The fileInfo contains the download URL.
     *
     * @param id template ID
     * @return import template fileInfo
     */
    @Operation(description = """
            Get the fileInfo of the import template by template ID.
            The fileInfo contains the download URL.""")
    @GetMapping("/getTemplateFile")
    public ApiResponse<FileInfo> getTemplateFile(@RequestParam(name = "id") Long id) {
        return ApiResponse.success(importService.getTemplateFile(id));
    }


    /** Models whose import templates belong only on their own page — see {@link TemplateScope}. */
    private Set<String> standaloneModelNames() {
        Filters standalone = new Filters().eq(ImportTemplate::getStandalone, true);
        return service.searchList(new FlexQuery(standalone)).stream()
                .map(ImportTemplate::getModelName)
                .collect(Collectors.toSet());
    }
}
