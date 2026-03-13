package io.softa.framework.orm.dto;

import java.io.InputStream;
import java.io.Serializable;
import lombok.Data;

import io.softa.framework.orm.enums.FileSource;
import io.softa.framework.orm.enums.FileType;

/**
 * The DTO of upload file.
 */
@Data
public class UploadFileDTO {

    private String modelName;

    private Serializable rowId;

    // Simple file name without extension
    private String fileName;

    private FileType fileType;

    // The file size in KB
    private Integer fileSize;

    private FileSource fileSource;

    private InputStream inputStream;
}
