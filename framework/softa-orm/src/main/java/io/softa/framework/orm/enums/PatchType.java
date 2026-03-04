package io.softa.framework.orm.enums;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * Patch operation type for OneToMany and ManyToMany fields
 */
@Getter
@AllArgsConstructor
public enum PatchType {
    /**
     * OneToMany patch operation type
     */
    CREATE("Create"),
    UPDATE("Update"),
    DELETE("Delete"),
    /**
     * ManyToMany patch operation type
     */
    ADD("Add"),
    REMOVE("Remove");

    @JsonValue
    private final String type;

    private static final Map<String, PatchType> patchTypeMap = new HashMap<>();

    static {
        Stream.of(values()).forEach(type -> patchTypeMap.put(type.getType().toLowerCase(), type));
    }

    /**
     * Parse patch type key by enum name or display name, ignoring case.
     *
     * @param key patch type key
     * @return PatchType or null
     */
    public static PatchType of(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        return patchTypeMap.get(key.trim().toLowerCase());
    }
}
