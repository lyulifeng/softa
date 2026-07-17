package io.softa.starter.permission.scope;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;

/**
 * Reads {@link DataScopeType} rows for {@link ScopeApplicabilityResolver}.
 *
 * <p>A separate bean on purpose — not inlined into the resolver — for two
 * reasons:
 *
 * <ol>
 *   <li><b>Bypass + no recursion.</b> {@code DataScopeType} is a reference model
 *       nobody grants a scope on, so a normal scoped read would fail-closed to
 *       zero rows; worse, the scope aspect routes back through
 *       {@code PermissionServiceImpl} → {@link ScopeApplicabilityResolver},
 *       which would recurse. {@link #read()} is {@code @SkipPermissionCheck} so
 *       {@code appendScopeAccessFilters} short-circuits on {@code shouldBypass()}
 *       and never touches the resolver. The {@code @SkipPermissionCheck}
 *       {@code @Around} only fires on a proxied (cross-bean) call, so the
 *       annotated method must live here and be invoked by the resolver through
 *       the proxy.</li>
 *   <li><b>Boot cycle.</b> {@code ObjectProvider<ModelService>} defers resolution
 *       to first request, never during wiring.</li>
 * </ol>
 */
@Component
public class DataScopeTypeReader {

    static final String MODEL = "DataScopeType";

    private final ObjectProvider<ModelService<?>> modelService;

    public DataScopeTypeReader(ObjectProvider<ModelService<?>> modelService) {
        this.modelService = modelService;
    }

    /**
     * All {@link DataScopeType} rows as raw maps. Empty when the model isn't
     * present yet (fresh DB before the seed lands) or ModelService isn't
     * available. {@code @SkipPermissionCheck} bypasses the scope aspect —
     * mandatory here (see class doc: fail-closed + resolver recursion).
     */
    @SkipPermissionCheck
    public List<Map<String, Object>> read() {
        if (!ModelManager.existModel(MODEL)) {
            return List.of();
        }
        ModelService<?> ms = modelService.getIfAvailable();
        if (ms == null) {
            return List.of();
        }
        return ms.searchList(MODEL, new FlexQuery());
    }
}
