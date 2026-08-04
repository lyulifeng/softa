package io.softa.starter.metadata.scanner;

import java.time.Month;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import io.softa.starter.metadata.scanner.annotation.AnnotationScanResult;
import io.softa.starter.metadata.entity.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ScannerScope}: the package matcher, the transitive
 * option-set selection, and the from-db scope filter.
 */
class ScannerScopeTest {

    // ---- matchesAll / isEmpty / matches ---------------------------------

    @Test
    void starSoleEntryMatchesAll() {
        ScannerScope scope = ScannerScope.of(List.of("*"));
        assertTrue(scope.matchesAll());
        assertFalse(scope.isEmpty());
        assertTrue(scope.matches("anything.at.all"));
    }

    @Test
    void nullOrEmptyManagesNothing() {
        assertTrue(ScannerScope.of(null).isEmpty());
        ScannerScope scope = ScannerScope.of(List.of());
        assertTrue(scope.isEmpty());
        assertFalse(scope.matchesAll());
        assertFalse(scope.matches("io.softa.foo"));
    }

    @Test
    void exactPackageDoesNotMatchSubpackage() {
        ScannerScope exact = ScannerScope.of(List.of("io\\.softa\\.foo"));
        assertTrue(exact.matches("io.softa.foo"));
        assertFalse(exact.matches("io.softa.foo.bar"));
    }

    @Test
    void subpackageWildcardMatchesBoth() {
        ScannerScope sub = ScannerScope.of(List.of("io\\.softa\\.foo.*"));
        assertTrue(sub.matches("io.softa.foo"));
        assertTrue(sub.matches("io.softa.foo.bar"));
        assertFalse(sub.matches("io.softa.other"));
    }

    @Test
    void multiplePatternsAreOred() {
        ScannerScope scope = ScannerScope.of(List.of("io\\.a.*", "io\\.b.*"));
        assertTrue(scope.matches("io.a.x"));
        assertTrue(scope.matches("io.b.y"));
        assertFalse(scope.matches("io.c.z"));
        assertFalse(scope.matchesAll());
        assertFalse(scope.isEmpty());
    }

    @Test
    void invalidRegexThrowsNamingTheOffendingEntry() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ScannerScope.of(List.of("io.softa.[")));
        assertTrue(ex.getMessage().contains("io.softa.["));
    }

    @Test
    void starIsAllAliasOnlyAsSoleEntry() {
        // In a multi-entry list "*" is a regex, and a bare "*" is an invalid
        // regex (dangling metacharacter) → loud failure, never the all-alias.
        assertThrows(IllegalArgumentException.class,
                () -> ScannerScope.of(List.of("*", "io\\.x.*")));
    }

    // ---- selectOptionSetEnums (transitive inclusion) --------------------

    @Test
    void selectsOptionSetEnumByPackage() {
        String pkg = Month.class.getPackageName();   // "java.time"
        ScannerScope scope = ScannerScope.of(List.of(Pattern.quote(pkg)));
        assertEquals(List.of(Month.class),
                scope.selectOptionSetEnums(List.of(Month.class), Set.of()));
    }

    @Test
    void transitivelyIncludesReferencedOutOfScopeEnum() {
        ScannerScope scope = ScannerScope.of(List.of("io\\.softa\\.nowhere.*"));  // not java.time
        // Not referenced + out of scope by package → excluded.
        assertTrue(scope.selectOptionSetEnums(List.of(Month.class), Set.of()).isEmpty());
        // Referenced by simple name (== optionSetCode) → pulled in despite package.
        assertEquals(List.of(Month.class),
                scope.selectOptionSetEnums(List.of(Month.class), Set.of("Month")));
    }

    // ---- filter (from-db scope confinement) -----------------------------

    @Test
    void filterKeepsInScopeKeysOnly() {
        AnnotationScanResult fromDb = new AnnotationScanResult(
                List.of(model("Customer"), model("Order")),
                List.of(field("Customer", "tier"), field("Order", "shippedAt")),
                List.of(optionSet("Tier"), optionSet("Currency")),
                List.of(optionItem("Tier", "g"), optionItem("Currency", "usd")),
                List.of(index("Customer", "uk_customer"), index("Order", "idx_order")));

        AnnotationScanResult out = partial().confineFromDb(fromDb,Set.of("Customer"), Set.of("Tier"));

        assertEquals(1, out.models().size());
        assertEquals("Customer", out.models().get(0).getModelName());
        assertEquals(1, out.fields().size());
        assertEquals("Customer", out.fields().get(0).getModelName());
        assertEquals(1, out.modelIndexes().size());
        assertEquals("Customer", out.modelIndexes().get(0).getModelName());
        assertEquals(1, out.optionSets().size());
        assertEquals("Tier", out.optionSets().get(0).getOptionSetCode());
        assertEquals(1, out.optionItems().size());
        assertEquals("Tier", out.optionItems().get(0).getOptionSetCode());
    }

    @Test
    void filterDropsOutOfScopeRowsEntirely() {
        // Cross-deletion guard: out-of-scope db rows vanish from fromDb, so a diff
        // against in-scope-only fromCode can never place them in the removed bucket.
        AnnotationScanResult fromDb = new AnnotationScanResult(
                List.of(model("Order")),
                List.of(field("Order", "shippedAt")),
                List.of(), List.of(),
                List.of(index("Order", "idx_order")));
        assertTrue(partial().confineFromDb(fromDb,Set.of("Customer"), Set.of()).isEmpty());
    }

    @Test
    void filterKeepsTransitivelyReferencedOptionSet() {
        // Orphan-prevention guard: a referenced out-of-scope-package enum code is
        // in inScopeOptionCodes, so its db rows are retained (not deleted).
        AnnotationScanResult fromDb = new AnnotationScanResult(
                List.of(), List.of(),
                List.of(optionSet("Tier")),
                List.of(optionItem("Tier", "g")),
                List.of());
        AnnotationScanResult out = partial().confineFromDb(fromDb,Set.of(), Set.of("Tier"));
        assertEquals(1, out.optionSets().size());
        assertEquals(1, out.optionItems().size());
    }

    @Test
    void confineAppliesUnderMatchAllToo_soCodelessRootsAreNeverDeleted() {
        // An aggregate root with no Java class is confined out under EVERY scope,
        // ["*"] included: the catalog cannot tell an orphan from a deliberately
        // code-less (Studio- / seed-authored) definition, so it is never placed in
        // the diff's removed bucket. Guards the regression where scanner-scope =
        // ["*"] wiped hand-authored model rows on every boot.
        AnnotationScanResult fromDb = new AnnotationScanResult(
                List.of(model("Customer"), model("Order")),
                List.of(field("Customer", "tier")),
                List.of(optionSet("Tier")),
                List.of(optionItem("Tier", "g")),
                List.of(index("Customer", "uk")));

        AnnotationScanResult out = ScannerScope.of(List.of("*"))
                .confineFromDb(fromDb, Set.of(), Set.of());

        assertTrue(out.isEmpty(), "no root exists in code → nothing may reach the diff");
    }

    @Test
    void confineUnderMatchAllKeepsRootsThatExistInCode_withTheirAttributes() {
        // The other half of the rule: when the root IS in code, its attribute rows
        // enter the diff so removed fields / indexes / option items still get
        // deleted — the annotations own the root's attribute set.
        AnnotationScanResult fromDb = new AnnotationScanResult(
                List.of(model("Customer"), model("Order")),
                List.of(field("Customer", "tier"), field("Order", "shippedAt")),
                List.of(optionSet("Tier")),
                List.of(optionItem("Tier", "g")),
                List.of(index("Customer", "uk"), index("Order", "idx_order")));

        AnnotationScanResult out = ScannerScope.of(List.of("*"))
                .confineFromDb(fromDb, Set.of("Customer"), Set.of("Tier"));

        assertEquals(1, out.models().size());
        assertEquals("Customer", out.models().get(0).getModelName());
        assertEquals(1, out.fields().size());
        assertEquals(1, out.modelIndexes().size());
        assertEquals(1, out.optionSets().size());
        assertEquals(1, out.optionItems().size());
    }

    // ---- fixtures -------------------------------------------------------

    /** A non-matchAll scope; {@code confineFromDb} then keys off the passed
     *  sets, independent of the scope's own package patterns. */
    private static ScannerScope partial() {
        return ScannerScope.of(List.of("io\\.partial.*"));
    }

    private static SysModel model(String name) {
        SysModel m = new SysModel();
        m.setModelName(name);
        return m;
    }

    private static SysField field(String model, String fieldName) {
        SysField f = new SysField();
        f.setModelName(model);
        f.setFieldName(fieldName);
        return f;
    }

    private static SysModelIndex index(String model, String indexName) {
        SysModelIndex idx = new SysModelIndex();
        idx.setModelName(model);
        idx.setIndexName(indexName);
        return idx;
    }

    private static SysOptionSet optionSet(String code) {
        SysOptionSet os = new SysOptionSet();
        os.setOptionSetCode(code);
        return os;
    }

    private static SysOptionItem optionItem(String code, String item) {
        SysOptionItem it = new SysOptionItem();
        it.setOptionSetCode(code);
        it.setItemCode(item);
        return it;
    }
}
