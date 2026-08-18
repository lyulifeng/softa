package io.softa.framework.web.controller;

import java.io.Serializable;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.softa.framework.base.exception.PermissionException;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.dto.FileInfo;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.service.FileService;
import io.softa.framework.orm.service.PermissionService;
import io.softa.framework.orm.utils.IdUtils;
import io.softa.framework.web.response.ApiResponse;

/**
 * FileController
 */
@Tag(name = "File")
@RestController
@RequestMapping("/file")
public class FileController {

    @Autowired
    private FileService service;

    @Autowired
    private PermissionService permissionService;

    /**
     * Authorize attaching a file to an existing row — a change to that row, so it asks what a change to
     * that row asks.
     *
     * <p>Two questions, because one cannot answer the other. <b>Which rows</b> is
     * {@code checkIdAccess}: it resolves to "is this id inside your scope", so it stops someone
     * attaching to a record they cannot even see. <b>Whether at all</b> is
     * {@code hasModelActionGrant}: {@code checkIdAccess} reads the same scope whatever access type it
     * is given, so on its own it lets a read-only caller write to every row they can read — and the
     * endpoint gate, which would normally have asked, cannot match these URLs and had to whitelist
     * them instead.
     */
    private void assertCanAttach(String modelName, Serializable rowId) {
        if (!permissionService.hasModelActionGrant(modelName, AccessType.UPDATE)) {
            throw new PermissionException("You may not attach files to " + modelName + ".");
        }
        permissionService.checkIdAccess(modelName, IdUtils.formatId(modelName, rowId), AccessType.UPDATE);
    }

    /**
     * Get the fileInfo by fileId
     */
    @Operation(description = "Get the fileInfo by fileId")
    @GetMapping(value = "/getByFileId")
    @Parameter(name = "fileId", description = "The id of the file object.")
    public ApiResponse<FileInfo> getByFileId(@RequestParam Long fileId) {
        // NOT FOR EXTERNAL USE — this endpoint must not be whitelisted.
        //
        // It is the one place a caller names a FileRecord by its own primary key, which is exactly the
        // question this design does not answer: access to a file derives from access to the row holding
        // it, and a bare id says nothing about any row. Reachable, it would be a direct object
        // reference over every file in the deployment. Business callers get their URLs from expanding
        // the File field on a row they already read; there is nothing here they need.
        //
        // Left in place for internal service callers and for administrators, who bypass the data plane
        // regardless. Whitelisting it re-opens the hole the expansion path was designed to avoid.
        Assert.notNull(fileId, "fileId cannot be empty.");
        return ApiResponse.success(service.getByFileId(fileId).orElse(null));
    }

    /**
     * Get the fileInfo by modelName and rowId
     */
    @Operation(description = "Get the fileInfos by modelName and rowId")
    @GetMapping(value = "/getRowFiles")
    @Parameters({
            @Parameter(name = "modelName", description = "The model name of the file belongs to"),
            @Parameter(name = "rowId", description = "The row ID of the file belongs to"),
    })
    public ApiResponse<List<FileInfo>> getRowFiles(@RequestParam String modelName,
                                                   @RequestParam Serializable rowId) {
        Assert.notBlank(modelName, "modelName cannot be empty.");
        Assert.notNull(rowId, "rowId cannot be null.");
        return ApiResponse.success(service.getRowFiles(modelName, rowId));
    }

    /**
     * Upload a file to the specified model and rowId, and return the fileInfo.
     *
     * @param modelName The model name of the file belongs to
     * @param rowId The row ID of the file belongs to
     * @param file The file to be uploaded
     * @return The fileInfo of the uploaded file
     */
    @Operation(description = "Upload a file to the specified model and row, and return the fileInfo.")
    @PostMapping("/uploadFileToRow")
    @Parameters({
            @Parameter(name = "modelName", description = "The model name of the file belongs to"),
            @Parameter(name = "rowId", description = "The row ID of the file belongs to"),
            @Parameter(name = "file", description = "The file to be uploaded")
    })
    public ApiResponse<FileInfo> uploadFileToRow(@RequestParam String modelName,
                                                 @RequestParam Serializable rowId,
                                                 @RequestParam MultipartFile file) {
        Assert.notBlank(modelName, "modelName cannot be empty.");
        Assert.notNull(rowId, "rowId cannot be null.");
        Assert.notTrue(file.isEmpty(), "The file to upload cannot be empty!");
        assertCanAttach(modelName, rowId);
        return ApiResponse.success(service.uploadFile(modelName, rowId, null, file));
    }

    /**
     * Upload a file to the specified model, rowId and fieldName, and return the fileInfo.
     *
     * @param modelName The model name of the file belongs to
     * @param rowId The row ID of the file belongs to, can be null in create mode
     * @param fieldName The field name of the file belongs to
     * @param file The file to be uploaded
     * @return The fileInfo of the uploaded file
     */
    @Operation(description = "Upload a file to the specified model and row, and return the fileInfo.")
    @PostMapping("/uploadFileToField")
    @Parameters({
            @Parameter(name = "modelName", description = "The model name of the file belongs to"),
            @Parameter(name = "rowId", description = "The row ID of the file belongs to, can be null in create mode"),
            @Parameter(name = "fieldName", description = "The field name of the file belongs to"),
            @Parameter(name = "file", description = "The file to be uploaded")
    })
    public ApiResponse<FileInfo> uploadFileToField(@RequestParam String modelName,
                                                 @RequestParam(required = false) Serializable rowId,
                                                 @RequestParam String fieldName,
                                                 @RequestParam MultipartFile file) {
        Assert.notBlank(modelName, "modelName cannot be empty.");
        Assert.notTrue(file.isEmpty(), "The file to upload cannot be empty!");
        if (rowId != null) {
            assertCanAttach(modelName, rowId);
        }
        // A null rowId is the create-form case: the record does not exist yet, so there is no row to
        // check against. What bounds it is the save — the file only enters business data when a row
        // references it, and that write runs FileOwnership.validate, which refuses an id this model
        // does not own. Until then the record is unclaimed and reachable through no row at all.
        return ApiResponse.success(service.uploadFile(modelName, rowId, fieldName, file));
    }
}