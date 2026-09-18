package io.softa.starter.file.dto;

import lombok.Data;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.starter.file.vo.PivotSpec;

/**
 * The DTO of Excel sheet info.
 */
@Data
public class SheetInfo {
    private String modelName;

    private String sheetName;

    private FlexQuery flexQuery;

    /** Pivot columns to append to this sheet; null for none. See {@link PivotSpec}. */
    private PivotSpec pivot;
}
