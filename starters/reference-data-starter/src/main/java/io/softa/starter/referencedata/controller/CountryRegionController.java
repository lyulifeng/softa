package io.softa.starter.referencedata.controller;

import java.util.Comparator;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.base.enums.SystemUser;
import io.softa.framework.orm.annotation.SwitchUser;
import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.referencedata.dto.DialCodeItemDTO;
import io.softa.starter.referencedata.entity.CountryRegion;
import io.softa.starter.referencedata.service.CountryRegionService;

/**
 * CountryRegion Controller. CRUD is served by the metadata-driven generic
 * endpoints; this class only adds the custom projections that the generic
 * pipeline cannot express.
 */
@Tag(name = "CountryRegion")
@RestController
@RequestMapping("/CountryRegion")
public class CountryRegionController extends EntityController<CountryRegionService, CountryRegion, String> {

    @Operation(summary = "List dial codes",
            description = "Returns one row per country for phone-input / SMS-region selectors. "
                    + "Ordered by English name asc. dialCode is not unique across countries.")
    // Public: the login screen needs dial codes to accept a mobile number, and it runs before any
    // session exists. Safe to open — the response is ISO 3166 / E.164 reference data, identical for
    // every caller and containing nothing about any tenant, account, or person.
    @SwitchUser(SystemUser.REGISTERED_USER)
    @GetMapping("/listDialCodes")
    public ApiResponse<List<DialCodeItemDTO>> listDialCodes() {
        List<DialCodeItemDTO> items = service.searchList().stream()
                .filter(c -> StringUtils.hasText(c.getDialCode()))
                .map(CountryRegionController::toDialCodeItem)
                .sorted(Comparator.comparing(DialCodeItemDTO::getName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return ApiResponse.success(items);
    }

    private static DialCodeItemDTO toDialCodeItem(CountryRegion c) {
        DialCodeItemDTO item = new DialCodeItemDTO();
        item.setCode(c.getId());
        item.setName(c.getName());
        item.setDialCode(c.getDialCode());
        item.setAlpha3Code(c.getAlpha3Code());
        return item;
    }
}
