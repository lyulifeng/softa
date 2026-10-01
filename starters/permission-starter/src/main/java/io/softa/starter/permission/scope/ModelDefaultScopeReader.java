package io.softa.starter.permission.scope;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.softa.starter.permission.entity.ModelDefaultScope;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;

/**
 * Reads {@link ModelDefaultScope} rows for {@link ModelDefaultScopeRegistry}.
 *
 * <p>A separate bean for the same two reasons {@code DataScopeTypeReader} is one:
 *
 * <ol>
 *   <li><b>Bypass + no recursion.</b> Nobody grants a scope on this model, so a normal scoped read
 *       would fail-closed to zero rows — and a registry that reads nothing would leave every model
 *       it exists to rescue empty, which is the very symptom it answers. Worse, the scope aspect
 *       routes back through {@code PermissionServiceImpl}, which consults this registry: reading it
 *       under scope would recurse. {@link #read()} is {@code @SkipPermissionCheck}, and that
 *       {@code @Around} only fires on a proxied cross-bean call — so the annotated method has to
 *       live here and be invoked through the proxy.</li>
 *   <li><b>Boot cycle.</b> {@code ObjectProvider<ModelService>} defers resolution to first use,
 *       never during wiring.</li>
 * </ol>
 */
@Component
public class ModelDefaultScopeReader {

    static final String MODEL = "ModelDefaultScope";

    private final ObjectProvider<ModelService<?>> modelService;

    public ModelDefaultScopeReader(ObjectProvider<ModelService<?>> modelService) {
        this.modelService = modelService;
    }

    /**
     * All rows as raw maps. Empty when the model is not present yet (a fresh database before the
     * seed lands) or {@code ModelService} is not available — both mean "nothing is declared",
     * which leaves every model on the path it took before this mechanism existed.
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
