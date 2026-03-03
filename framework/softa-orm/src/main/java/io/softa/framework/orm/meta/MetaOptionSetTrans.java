package io.softa.framework.orm.meta;

import java.io.Serial;
import java.io.Serializable;
import lombok.*;

/**
 * MetaOptionSetTrans object
 */
@Getter
@Setter(AccessLevel.PACKAGE)
@ToString
@EqualsAndHashCode
public class MetaOptionSetTrans implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String languageCode;

    private Long rowId;

    private String name;

    private String description;
}