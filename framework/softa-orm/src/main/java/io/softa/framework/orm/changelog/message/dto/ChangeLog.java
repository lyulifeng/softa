package io.softa.framework.orm.changelog.message.dto;

import java.util.List;
import java.util.Map;
import lombok.Data;

import io.softa.framework.orm.enums.AccessType;

/**
 * ChangeLog DTO
 */
@Data
public class ChangeLog {

    // ChangeLog uuid
    private String uuid;

    // Trace ID for distributed tracing
    private String traceId;

    private String model;
    private String rowId;
    private AccessType accessType;

    private Map<String, Object> dataBeforeChange;
    private Map<String, Object> dataAfterChange;

    /**
     * The records this row pointed at, as {@code field=id} for each many-to-one it held.
     *
     * <p>Indexed so a row's history can be found by its parent, which the payload cannot answer:
     * the payload is stored as an unindexed string. That matters most once the row is gone — a
     * deleted family member has no id left to ask about, but its log still says
     * {@code employeeId=100}. Taken from what the log saw: a creation and a deletion carry the
     * whole row, so they always name the parent; an update carries only what it changed, so it
     * names a parent only when it moved the row — under both the old one and the new — and an
     * update to a row that stayed put is found by the row's own id instead.
     */
    private List<String> refs;

    /**
     * On an update, the fields it wrote. Indexed so an update can be judged by what it touched
     * without reading the payload — a reader whose sensitive field sets cover every field an update
     * wrote is not shown it, and this lets the index leave it out of the count as well as the page.
     */
    private List<String> changedFields;

    private Long tenantId;
    private Long changedById;
    private String changedBy;
    private String changedTime;

}
