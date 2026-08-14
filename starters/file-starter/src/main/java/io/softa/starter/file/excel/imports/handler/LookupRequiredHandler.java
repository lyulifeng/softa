package io.softa.starter.file.excel.imports.handler;

import io.softa.framework.orm.meta.MetaField;
import io.softa.starter.file.dto.ImportFieldDTO;

/**
 * Enforces {@code required} on a relation column mapped by a lookup path ({@code legalEntityId.code}),
 * and does nothing else.
 *
 * <p>Those columns are resolved later, in bulk, by {@code RelationLookupResolver}, so they carry no
 * handler of their own — which also meant they carried no required check. A missing mandatory relation
 * therefore travelled all the way to the write and came back as the ORM's
 * {@code Model field Department:legalEntityId is a required field and cannot be null!} — a model field
 * name, on a sheet whose columns are called "Legal Entity Code". Every other column type reports its own
 * header.
 *
 * <p>Requiredness comes from the <b>root</b> segment ({@code legalEntityId}), not the last one: the
 * column fills the relation on the main row, and whether the related model happens to require its own
 * {@code code} says nothing about whether this row needs the relation at all.
 *
 * <p>{@link #handleValue} is a pass-through: the cell must reach the resolver exactly as written.
 */
public class LookupRequiredHandler extends BaseImportHandler {

    public LookupRequiredHandler(MetaField rootField, ImportFieldDTO importFieldDTO, String columnPath) {
        super(rootField, importFieldDTO);
        // The row is keyed by the dotted column, not by the root field name.
        rowKey(columnPath);
    }

    @Override
    public Object handleValue(Object value) {
        return value;
    }
}
