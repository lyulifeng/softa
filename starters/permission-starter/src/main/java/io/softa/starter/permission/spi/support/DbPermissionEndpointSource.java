package io.softa.starter.permission.spi.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.softa.framework.base.utils.JsonUtils;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.permission.spi.PermissionEndpointSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Standalone default {@link PermissionEndpointSource} — reads the RBAC
 * {@code Permission} rows and their {@code Navigation} → model straight from the
 * DB by model name (约定读, no entity classes), so {@code permission-starter} can
 * build its {@code EndpointIndex} with no {@code user-starter} on the classpath.
 * Registered only when no other source is present
 * ({@code @ConditionalOnMissingBean}); in the monolith {@code user-starter}'s
 * {@code PermissionEndpointSourceImpl} wins.
 *
 * <p>Queried once at {@code EndpointIndex} {@code @PostConstruct} (not per
 * request), so the DB read is one-time at boot. Graceful degradation: when the
 * {@code Permission} model isn't present (non-RBAC app) it returns empty.
 */
@Slf4j
@RequiredArgsConstructor
public class DbPermissionEndpointSource implements PermissionEndpointSource {

    private static final String PERMISSION_MODEL = "Permission";
    private static final String NAVIGATION_MODEL = "Navigation";
    private static final String F_ID = "id";
    private static final String F_NAVIGATION_ID = "navigationId";
    private static final String F_ENDPOINTS = "endpoints";
    private static final String F_MODEL = "model";

    private final ModelService<?> modelService;

    @Override
    public List<PermissionEndpointDef> getPermissionEndpoints() {
        if (!ModelManager.existModel(PERMISSION_MODEL)) {
            log.debug("DbPermissionEndpointSource — model '{}' absent; no endpoints", PERMISSION_MODEL);
            return List.of();
        }
        Map<String, String> navModel = loadNavigationModels();
        List<Map<String, Object>> rows = modelService.searchList(
                PERMISSION_MODEL, new FlexQuery(List.of(F_ID, F_NAVIGATION_ID, F_ENDPOINTS)));
        List<PermissionEndpointDef> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            String id = asString(r.get(F_ID));
            if (id == null) continue;
            List<String> explicit = JsonUtils.toStringList(r.get(F_ENDPOINTS));
            String navId = asString(r.get(F_NAVIGATION_ID));
            String model = navId == null ? null : navModel.get(navId);
            out.add(new PermissionEndpointDef(id, explicit, model));
        }
        return out;
    }

    /** {@code navigationId → model} for every navigation, resolved in one query;
     *  empty when the Navigation model is absent. */
    private Map<String, String> loadNavigationModels() {
        if (!ModelManager.existModel(NAVIGATION_MODEL)) return Map.of();
        List<Map<String, Object>> rows = modelService.searchList(
                NAVIGATION_MODEL, new FlexQuery(List.of(F_ID, F_MODEL)));
        Map<String, String> out = new HashMap<>(rows.size());
        for (Map<String, Object> r : rows) {
            String id = asString(r.get(F_ID));
            String model = asString(r.get(F_MODEL));
            if (id != null && model != null && !model.isEmpty()) out.put(id, model);
        }
        return out;
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }
}
