package io.softa.starter.metadata.scanner;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.softa.starter.metadata.entity.*;
import io.softa.starter.metadata.scanner.annotation.AnnotationScanResult;

/**
 * Immutable package-scope matcher for the metadata annotation scanner.
 *
 * <p>Built from {@code system.metadata.scanner-scope} (see
 * {@link io.softa.starter.metadata.config.MetadataProperties#scannerScope()}).
 * Each pattern is a regex {@linkplain java.util.regex.Matcher#matches()
 * full-matched} against a class's {@linkplain Class#getPackageName() package
 * name}. A sole entry {@code "*"} is a fast-path alias for "match every
 * package"; a {@code null} / empty list matches nothing.
 *
 * <p>Match semantics are full-match, so dots must be escaped:
 * {@code io\.softa\.foo.*} includes sub-packages, {@code io\.softa\.foo} matches
 * that package only. {@code "*"} is the all-alias only as a <em>sole</em> entry;
 * inside a multi-entry list it is treated as a regex.
 *
 * <p>Also hosts the static filter helper used by the scanner to confine
 * the from-db {@link AnnotationScanResult} to the in-scope key set, so
 * out-of-scope rows never enter the diff (and therefore never get deleted).
 */
public final class ScannerScope {

    private static final String MATCH_ALL = "*";

    private final boolean matchAll;
    private final List<Pattern> patterns;

    private ScannerScope(boolean matchAll, List<Pattern> patterns) {
        this.matchAll = matchAll;
        this.patterns = patterns;
    }

    /**
     * Build a scope from raw config patterns. {@code null} / empty ⇒ matches
     * nothing; a sole {@code "*"} ⇒ matches all; otherwise each non-blank entry
     * is compiled as a regex.
     *
     * @throws IllegalArgumentException if any entry is not a valid regex
     */
    public static ScannerScope of(List<String> rawPatterns) {
        if (rawPatterns == null || rawPatterns.isEmpty()) {
            return new ScannerScope(false, List.of());
        }
        if (rawPatterns.size() == 1 && MATCH_ALL.equals(rawPatterns.getFirst().trim())) {
            return new ScannerScope(true, List.of());
        }
        List<Pattern> compiled = new ArrayList<>(rawPatterns.size());
        for (String raw : rawPatterns) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                compiled.add(Pattern.compile(raw.trim()));
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(
                        "Invalid system.metadata.scanner-scope regex: '" + raw + "' — " + e.getMessage(), e);
            }
        }
        return new ScannerScope(false, compiled);
    }

    /** {@code true} when a sole {@code "*"} entry was configured. */
    public boolean matchesAll() {
        return matchAll;
    }

    /** {@code true} when the scope manages nothing ({@code null} / empty config). */
    public boolean isEmpty() {
        return !matchAll && patterns.isEmpty();
    }

    /** Whether the given package name falls within scope. */
    public boolean matches(String packageName) {
        if (matchAll) {
            return true;
        }
        if (packageName == null) {
            return false;
        }
        for (Pattern p : patterns) {
            if (p.matcher(packageName).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Select the option-set enums in scope: those whose package matches, plus
     * any whose simple name (== {@code optionSetCode}) is referenced by an
     * in-scope model's field. The transitive inclusion ensures a shared enum in
     * an out-of-scope package is pulled in rather than orphaned/deleted.
     *
     * @param allOptionSetEnums all {@code @OptionSet} enums on the classpath
     * @param referencedCodes   option-set codes referenced by in-scope models
     * @return the in-scope enums (package-matched ∪ referenced), de-duplicated
     */
    public List<Class<?>> selectOptionSetEnums(
            Collection<Class<?>> allOptionSetEnums, Set<String> referencedCodes) {
        LinkedHashSet<Class<?>> selected = new LinkedHashSet<>();
        Map<String, Class<?>> byCode = new LinkedHashMap<>();
        for (Class<?> e : allOptionSetEnums) {
            if (matches(e.getPackageName())) {
                selected.add(e);
            }
            byCode.putIfAbsent(e.getSimpleName(), e);
        }
        if (referencedCodes != null) {
            for (String code : referencedCodes) {
                Class<?> e = byCode.get(code);
                if (e != null) {
                    selected.add(e);
                }
            }
        }
        return new ArrayList<>(selected);
    }

    // ---- scope-confined view of from-db rows ---------------------------

    /**
     * Confine a from-db {@link AnnotationScanResult} to the models / option sets that exist in
     * code, so the diff only ever compares like with like. Rows are kept only when their model
     * name / option-set code is in the supplied in-scope key set (both derived from the parsed
     * annotations); everything else is dropped here and can never enter the diff's
     * {@code removed} bucket.
     *
     * <p><b>An aggregate root is never auto-deleted.</b> A {@code SysModel} / {@code SysOptionSet}
     * row whose class is absent — deleted from the source, or authored by the Studio no-code lane
     * / a metadata seed and never having had one — is confined out together with ITS fields,
     * indexes and option items: the attributes follow their root, so the definition is left whole
     * rather than reduced to a headless or field-less husk. This holds under <b>every</b> scope,
     * {@code ["*"]} included: nothing in the catalog records row ownership, so "orphan" and
     * "deliberately code-less" are indistinguishable, and guessing wrong silently destroys
     * hand-authored definitions. Same asymmetry as the physical schema — additive changes
     * auto-apply, destroying a whole definition is reported for a human to decide
     * ({@code MetadataAnnotationScanner} logs the names plus copy-paste SQL under {@code ["*"]}).
     *
     * <p>Field / option-item / index removals on a model whose class <i>is</i> present are still
     * applied: there the annotations are the single source of truth for the root's attributes.
     *
     * @param fromDb             all rows loaded from sys_*
     * @param inScopeModelNames  model names present in the in-scope annotations
     * @param inScopeOptionCodes option-set codes present in the in-scope annotations
     * @return the subset whose aggregate root exists in code
     */
    public AnnotationScanResult confineFromDb(
            AnnotationScanResult fromDb,
            Set<String> inScopeModelNames,
            Set<String> inScopeOptionCodes) {

        List<SysModel> models = new ArrayList<>();
        for (SysModel m : fromDb.models()) {
            if (inScopeModelNames.contains(m.getModelName())) {
                models.add(m);
            }
        }
        List<SysField> fields = new ArrayList<>();
        for (SysField f : fromDb.fields()) {
            if (inScopeModelNames.contains(f.getModelName())) {
                fields.add(f);
            }
        }
        List<SysModelIndex> indexes = new ArrayList<>();
        for (SysModelIndex idx : fromDb.modelIndexes()) {
            if (inScopeModelNames.contains(idx.getModelName())) {
                indexes.add(idx);
            }
        }
        List<SysOptionSet> optionSets = new ArrayList<>();
        for (SysOptionSet os : fromDb.optionSets()) {
            if (inScopeOptionCodes.contains(os.getOptionSetCode())) {
                optionSets.add(os);
            }
        }
        List<SysOptionItem> optionItems = new ArrayList<>();
        for (SysOptionItem item : fromDb.optionItems()) {
            if (inScopeOptionCodes.contains(item.getOptionSetCode())) {
                optionItems.add(item);
            }
        }
        return new AnnotationScanResult(models, fields, optionSets, optionItems, indexes);
    }
}
