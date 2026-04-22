package io.softa.framework.orm.domain;

import java.io.InputStream;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.softa.framework.orm.enums.FileType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FileObject")
public class FileStream {

    @Schema(description = "File Name")
    private String fileName;

    @Schema(description = "File Type")
    private FileType fileType;

    @Schema(description = "File Size in KB")
    private int fileSize;

    @Schema(description = "File InputStream")
    private InputStream inputStream;

}
