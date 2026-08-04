package io.softa.starter.metadata.controller.dto;

import io.softa.starter.metadata.scanner.MetadataStatus;

/**
 * Response of {@code GET /metadata/status}: the app identity plus the boot-time metadata
 * health snapshot ({@link MetadataStatus}, {@code null} until a scan has completed).
 *
 * @param appCode the runtime's {@code system.app-code}
 * @param inSync  whether the code and catalog fingerprints matched at the last scan;
 *                {@code null} while no snapshot exists
 * @param status  the full snapshot (fingerprints, physical drift report, timestamp)
 */
public record MetadataStatusDTO(String appCode, Boolean inSync, MetadataStatus status) {

    public static MetadataStatusDTO of(String appCode, MetadataStatus status) {
        return new MetadataStatusDTO(appCode, status == null ? null : status.inSync(), status);
    }
}
