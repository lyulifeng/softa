package io.softa.framework.orm.jdbc.pipeline.factory;

import lombok.NoArgsConstructor;

import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.jdbc.pipeline.processor.FieldProcessor;
import io.softa.framework.orm.jdbc.pipeline.processor.FilesGroupProcessor;
import io.softa.framework.orm.meta.MetaField;

/**
 * The processor factory of creating FilesGroupProcessor, which is processing File fields and MultiFile fields.
 */
@NoArgsConstructor
public class FilesGroupProcessorFactory implements FieldProcessorFactory {

    private ConvertType convertType;

    // the FilesGroupProcessor object to process File fields and MultiFile fields
    private FilesGroupProcessor filesGroupProcessor;

    public FilesGroupProcessorFactory(ConvertType convertType) {
        this.convertType = convertType;
    }

    /**
     * Create a field processor according to the field metadata.
     *
     * @param metaField field metadata object
     * @param accessType access type
     */
    @Override
    public FieldProcessor createProcessor(MetaField metaField, AccessType accessType) {
        FieldType fieldType = metaField.getFieldType();
        if (FieldType.FILE_TYPES.contains(fieldType)) {
            if (this.filesGroupProcessor == null) {
                this.filesGroupProcessor = new FilesGroupProcessor(metaField, accessType, convertType);
                return this.filesGroupProcessor;
            } else {
                this.filesGroupProcessor.addFileField(metaField);
            }
        }
        return null;
    }

}
