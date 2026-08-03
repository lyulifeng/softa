package io.softa.starter.studio.meta.support;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.utils.IdUtils;
import io.softa.starter.studio.meta.entity.DesignField;
import io.softa.starter.studio.meta.entity.DesignModel;
import io.softa.starter.studio.meta.service.DesignFieldService;
import io.softa.starter.studio.meta.service.DesignModelService;

/**
 * Materializes the optimistic-lock starting value ({@link ModelConstant#DEFAULT_VERSION}) onto a
 * {@code version} DesignField write row when its model has {@code versionLock} and no default is
 * declared — the studio-lane counterpart of the {@code AnnotationParser} materialization, so the
 * catalog ({@code design_field} → deployed {@code sys_field}) always carries the real starting
 * version and the DDL layer renders {@code DEFAULT 0}. An explicitly authored default wins.
 *
 * <p>Field-write hop only: enabling {@code versionLock} on a model whose {@code version} field
 * already exists does not backfill that field's row — the next save of the field does (this stamp
 * re-resolves on every write), and {@code ModelManager.validateVersionField} fail-fasts on deploy
 * of a versionLock model whose version field still carries no default.
 */
@Component
public class DesignVersionDefaultStamper {

    @Autowired
    private DesignFieldService fieldService;

    @Autowired
    private DesignModelService modelService;

    /**
     * Resolve and stamp the version default into a single write row. Rows that are not a
     * {@code version} field, already carry a default (in the row or persisted), or whose model
     * does not use {@code versionLock} are left untouched.
     */
    public void stamp(Map<String, Object> row) {
        if (row == null) {
            return;
        }
        Object idValue = row.get("id");
        DesignField existing = idValue != null
                ? fieldService.getById(IdUtils.convertIdToLong(idValue)).orElse(null) : null;

        String fieldName = resolveString(row, "fieldName", existing == null ? null : existing.getFieldName());
        if (!ModelConstant.VERSION.equals(fieldName)) {
            return;
        }
        String defaultValue = resolveString(row, "defaultValue", existing == null ? null : existing.getDefaultValue());
        if (StringUtils.hasText(defaultValue)) {
            return;
        }
        String modelName = resolveString(row, "modelName", existing == null ? null : existing.getModelName());
        Long appId = row.get("appId") != null
                ? IdUtils.convertIdToLong(row.get("appId")) : (existing == null ? null : existing.getAppId());
        // Same env-scoped lookup rationale as DesignFieldRelationStamper: design rows are per-env,
        // so the owning model must be resolved within THIS env. On create the envId is already
        // stamped by DesignWriteStamper.stampCreate; on update it is carried by `existing`.
        Long envId = row.get("envId") != null
                ? IdUtils.convertIdToLong(row.get("envId")) : (existing == null ? null : existing.getEnvId());
        if (modelName == null || appId == null || envId == null) {
            return;
        }
        DesignModel model = modelService.searchOne(new FlexQuery(new Filters()
                .eq(DesignModel::getAppId, appId)
                .eq(DesignModel::getEnvId, envId)
                .eq(DesignModel::getModelName, modelName))).orElse(null);
        if (model != null && Boolean.TRUE.equals(model.getVersionLock())) {
            row.put("defaultValue", String.valueOf(ModelConstant.DEFAULT_VERSION));
        }
    }

    private static String resolveString(Map<String, Object> row, String key, String fallback) {
        if (row.containsKey(key)) {
            Object value = row.get(key);
            return value == null ? null : String.valueOf(value);
        }
        return fallback;
    }
}
