package io.softa.starter.file.vo;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * A pivot declaration for an export: one column per key of a related long table, the cell being
 * that key's value for the exported row — "one column per employment type" for an FTE report.
 *
 * <p>The same object the list view renders its pivot columns from, sent along with a dynamic export
 * so the sheet carries the columns the screen shows. Nothing is stored in this shape: the source is a
 * long table (one row per primary row and key) because the key set is tenant data — a country's
 * employment types — and the pivot is a presentation of it, computed here and on screen by one rule.
 */
@Data
@Schema(name = "PivotSpec", description = "One column per key of a related long table, appended to a dynamic export")
public class PivotSpec implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "The long-table model holding one row per (primary row, key)", example = "DeptFteTypeStats")
    private String source;

    @Schema(description = "The field on `source` that points at the exported row", example = "deptFteStatsId")
    private String joinField;

    @Schema(description = "The field on `source` naming the key — a to-one onto `keyModel`", example = "employmentType")
    private String keyField;

    @Schema(description = "The model whose rows are the column set", example = "EmploymentType")
    private String keyModel;

    @Schema(description = "The field on `keyModel` that groups the keys; orders and labels the columns and decides applicability", example = "country")
    private String keyModelGroupField;

    @Schema(description = "The field on the exported model carrying the row's group, a to-one whose target has `keyModelGroupField` too", example = "companyId")
    private String rowGroupField;

    @Schema(description = "The field on `source` whose value fills the cell", example = "headcount")
    private String valueField;

    @Schema(description = "What a cell shows when the column's group does not apply to the row", example = "—")
    private String emptyText;

    @Schema(description = "Append the group to the column label: `Full Time (SG)`. The client decides, so screen and sheet agree", example = "true")
    private Boolean suffixGroup;
}
