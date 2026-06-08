package io.softa.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ID generation strategy.
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "ID Strategy")
public enum IdStrategy {
    /**
     * Database Auto-increment ID
     * Type: Long (64-bit)
     */
    @OptionItem(label = "DB Auto-increment ID")
    DB_AUTO_ID("DbAutoID"),

    /**
     * Distributed Unique ID of a Long type (64-bit).
     * An implementation of SnowflakeId by CosID library.
     * Time-sorted, with 41-bit timestamp, 10-bit machine ID, and 12-bit sequence number.
     * Suitable for distributed systems to ensure uniqueness across multiple nodes.
     */
    @OptionItem(label = "Distributed Long ID")
    DISTRIBUTED_LONG("DistributedLong"),

    /**
     * Distributed Unique ID of a String type.
     * An implementation of SnowflakeId by CosID library.
     * Encoded in Base36, resulting in a 13-character string.
     * Also, it can be configured to be Base62, resulting in an 11-character string.
     * Suitable for distributed systems without large-scale data volume requirements.
     */
    @OptionItem(label = "Distributed String ID")
    DISTRIBUTED_STRING("DistributedString"),

    // External ID: external input ID
    @OptionItem(label = "External ID")
    EXTERNAL_ID("ExternalID");

    @JsonValue
    private final String type;

}
