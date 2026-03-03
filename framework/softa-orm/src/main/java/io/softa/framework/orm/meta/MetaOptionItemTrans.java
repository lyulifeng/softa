package io.softa.framework.orm.meta;

import java.io.Serial;
import java.io.Serializable;
import lombok.*;

/**
 * MetaOptionItemTrans object
 */
@Getter
@Setter(AccessLevel.PACKAGE)
@ToString
@EqualsAndHashCode
public class MetaOptionItemTrans implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String languageCode;

    private Long rowId;

    private String itemName;

    private String description;
}