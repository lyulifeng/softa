package io.softa.starter.metadata.sequence.controller;

import io.softa.framework.web.controller.EntityController;
import io.softa.starter.metadata.sequence.entity.SysSequence;
import io.softa.starter.metadata.sequence.service.SysSequenceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST binding marker for {@link SysSequence}.
 *
 * <p>Generic CRUD verbs (searchList / getById / updateOne / etc.) are served
 * by the framework's catch-all {@code ModelController} via the
 * {@code /SysSequence/...} URL pattern; this class exists so OpenAPI/Swagger
 * documents the model under a dedicated tag and so request path resolution
 * picks up the {@code @RequestMapping} prefix.
 *
 * <p>v1 admin policy (enforced at config-validation layer, not by removing
 * endpoints): {@code createOne} / {@code deleteById} are forbidden for
 * tenant admins; {@code updateOne} only accepts changes to template /
 * startValue / mode / cadence / description. Tenant bootstrap is done via
 * {@code SysPreDataService.loadPreTenantData} on the JSON files in
 * {@code resources/data-tenant/}.
 */
@Tag(name = "SysSequence")
@RestController
@RequestMapping("/SysSequence")
public class SysSequenceController extends EntityController<SysSequenceService, SysSequence, Long> {
}
