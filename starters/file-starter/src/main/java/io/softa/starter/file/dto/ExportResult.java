package io.softa.starter.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.softa.framework.orm.dto.FileInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportResult {

    private FileInfo fileInfo;

    private Integer totalRows;
}
