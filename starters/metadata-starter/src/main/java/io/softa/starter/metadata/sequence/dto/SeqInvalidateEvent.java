package io.softa.starter.metadata.sequence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload broadcast across instances when a {@link
 * io.softa.starter.metadata.sequence.entity.SysSequence} row is mutated.
 * Receiving listeners drop the matching cache entry; v1 has no runtime
 * registry rebuild — new sequence codes go through the normal release
 * process and are picked up by {@code initialize()} at the next app start.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeqInvalidateEvent {

    /** Tenant the affected (tenant, code) belongs to. */
    private Long tenantId;

    /** Sequence code whose config cache entry should be evicted. */
    private String code;
}
