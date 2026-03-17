package io.softa.framework.orm.jdbc.pipeline.processor;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import io.softa.framework.orm.dto.FileInfo;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.utils.IdUtils;
import io.softa.framework.orm.utils.ReflectTool;

/**
 * File and MultiFile fields group processor.
 * Query from the fileRecords for one time, and then process the File and MultiFile fields in a batch.
 */
@Slf4j
public class FilesGroupProcessor extends BaseProcessor {

    private final ConvertType convertType;

    private final List<MetaField> fileFields = new ArrayList<>(0);

    /**
     * Constructor of the File and MultiFile fields group processor.
     *
     * @param firstField the first file field
     * @param accessType access type
     */
    public FilesGroupProcessor(MetaField firstField, AccessType accessType, ConvertType convertType) {
        super(firstField, accessType);
        this.convertType = convertType;
        this.fileFields.add(firstField);
    }

    /**
     * Add a File or MultiFile field to the group processor.
     *
     * @param fileField File or MultiFile field
     */
    public void addFileField(MetaField fileField) {
        this.fileFields.add(fileField);
    }

    /**
     * @param row The single-row data to be created or updated
     */
    @Override
    public void processInputRow(Map<String, Object> row) {
        for (MetaField fileField : fileFields) {
            String fieldName = fileField.getFieldName();
            boolean isContain = row.containsKey(fieldName);
            checkReadonly(isContain);
            Object obj = row.get(fieldName);
            // Check the required field
            if (AccessType.CREATE.equals(accessType)) {
                checkRequired(obj);
                return;
            } else if (isContain) {
                // If the field is set to null, check if it is a required field.
                checkRequired(obj);
            }
            if (FieldType.FILE.equals(fileField.getFieldType())) {
                obj = IdUtils.convertIdToLong(obj);
                row.put(fieldName, obj);
            } else if (obj instanceof List<?> listValue) {
                obj = StringUtils.join(listValue, ",");
                row.put(fieldName, obj);
            }
        }
    }

    /**
     * Batch process the output data of the File and MultiFile fields group.
     *
     * @param rows The list of output data
     */
    public void batchProcessOutputRows(List<Map<String, Object>> rows) {
        List<Long> fileIds = processFileIds(rows);
        if (CollectionUtils.isEmpty(fileIds) || !ConvertType.REFERENCE.equals(convertType)) {
            return;
        }
        List<FileInfo> fileInfos = ReflectTool.getByFileIds(fileIds);
        Map<Long, FileInfo> fileInfoMap = fileInfos.stream()
                .collect(Collectors.toMap(FileInfo::getFileId, fileInfo -> fileInfo));
        for (Map<String, Object> row : rows) {
            for (MetaField fileField : fileFields) {
                String fieldName = fileField.getFieldName();
                if (FieldType.FILE.equals(fileField.getFieldType()) && row.get(fieldName) != null) {
                    row.put(fieldName, fileInfoMap.get((Long) row.get(fieldName)));
                } else if (FieldType.MULTI_FILE.equals(fileField.getFieldType()) &&
                        row.get(fieldName) instanceof List<?> fileIdList) {
                    List<FileInfo> fileInfoList = fileIdList.stream()
                            .map(fileId -> fileInfoMap.get((Long) fileId))
                            .filter(Objects::nonNull)
                            .toList();
                    row.put(fieldName, fileInfoList);
                }
            }
        }
    }

    /**
     * Get the fileIds from the rows.
     *
     * @param rows The list of rows
     * @return The list of fileIds
     */
    private List<Long> processFileIds(List<Map<String, Object>> rows) {
        List<Long> fileIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            fileFields.forEach(fileField -> {
                String fieldName = fileField.getFieldName();
                Object fileId = row.get(fieldName);
                if (FieldType.FILE.equals(fileField.getFieldType()) &&
                        Objects.nonNull(fileId)) {
                    Long id = IdUtils.convertIdToLong(fileId);
                    fileIds.add(id);
                    row.put(fieldName, id);
                } else if (FieldType.MULTI_FILE.equals(fileField.getFieldType())) {
                    String strIds = (String) fileId;
                    if (StringUtils.isBlank(strIds)) {
                        row.put(fieldName, null);
                    } else {
                        List<Long> fileIdList = Arrays.stream(StringUtils.split(strIds, ","))
                                .map(IdUtils::convertIdToLong)
                                .toList();
                        fileIds.addAll(fileIdList);
                        row.put(fieldName, fileIdList);
                    }
                }
            });
        }
        return fileIds;
    }
}
