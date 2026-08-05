package io.softa.starter.metadata.scanner;

import java.time.LocalDateTime;

import io.softa.starter.metadata.ddl.introspect.PhysicalDriftReport;

/**
 * The boot-time metadata health snapshot, recorded by whichever posture ran — the
 * {@code MetadataAnnotationScanner} (active scope) or the {@code MetadataAnnotationChecker}
 * (empty scope) — and served by {@code GET /metadata/status}, so "did my change reach this
 * runtime?" is one call instead of a debugging session.
 *
 * <p>Point-in-time by design: it describes the last boot-time scan (runtime metadata applied
 * later, e.g. a studio envelope, is not reflected until the next boot / reload). Fingerprints
 * cover the posture's own universe — the scanner's in-scope packages, or everything the
 * checker discovers.
 *
 * @param source             {@code "scanner"} or {@code "checker"}
 * @param codeFingerprint    {@link io.softa.starter.metadata.checksum.CatalogFingerprint} of the
 *                           annotation-derived catalog
 * @param catalogFingerprint fingerprint of the {@code sys_*} catalog ({@code == codeFingerprint}
 *                           after a scanner reconcile by construction)
 * @param reconciled         whether this runtime actively reconciles (scanner posture)
 * @param appliedChanges     {@code sys_*} row changes applied at this boot (scanner; 0 = idempotent)
 * @param physicalDrift      the physical audit result, or {@code null} when introspection was
 *                           off / unavailable
 * @param checkedAt          when the snapshot was taken
 */
public record MetadataStatus(
        String source,
        String codeFingerprint,
        String catalogFingerprint,
        boolean reconciled,
        int appliedChanges,
        PhysicalDriftReport physicalDrift,
        LocalDateTime checkedAt) {

    private static volatile MetadataStatus current;

    /** Record this boot's snapshot (last writer wins — exactly one posture runs per boot). */
    public static void record(MetadataStatus status) {
        current = status;
    }

    /** The last recorded snapshot, or {@code null} when no scan has completed yet. */
    public static MetadataStatus current() {
        return current;
    }

    /** Whether the code and catalog fingerprints match (the "did my change land" answer). */
    public boolean inSync() {
        return codeFingerprint != null && codeFingerprint.equals(catalogFingerprint);
    }
}
