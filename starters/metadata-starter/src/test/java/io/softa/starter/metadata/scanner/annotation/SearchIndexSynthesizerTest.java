package io.softa.starter.metadata.scanner.annotation;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IndexMethod;
import io.softa.starter.metadata.entity.SysModelIndex;
import io.softa.starter.metadata.scanner.annotation.SearchIndexSynthesizer.DeclaredIndex;
import io.softa.starter.metadata.scanner.annotation.SearchIndexSynthesizer.FieldSpec;
import io.softa.starter.metadata.scanner.annotation.SearchIndexSynthesizer.ModelSpec;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The derivation that spares every model a hand-written search index — see
 * {@link SearchIndexSynthesizer}.
 */
class SearchIndexSynthesizerTest {

    private static FieldSpec text(String fieldName, String columnName) {
        return new FieldSpec(fieldName, columnName, FieldType.STRING, false);
    }

    private static ModelSpec model(String modelName, String tableName, List<String> searchName,
                                   FieldSpec... fields) {
        return new ModelSpec(modelName, tableName, searchName, false, true, List.of(fields));
    }

    private static List<SysModelIndex> derive(ModelSpec... models) {
        return SearchIndexSynthesizer.derive(List.of(models), List.of());
    }

    @Test
    void explicitSearchName_yieldsOneSearchIndexPerMember() {
        List<SysModelIndex> derived = derive(model("Department", "department", List.of("code", "name"),
                text("code", "code"), text("name", "name"), text("note", "note")));

        assertEquals(2, derived.size());
        SysModelIndex first = derived.get(0);
        assertEquals("idx_department_code_search", first.getIndexName());
        assertEquals("Department", first.getModelName());
        // Field names, not column names: the DDL context builder maps field -> column itself.
        assertEquals(List.of("code"), first.getIndexFields());
        assertEquals(IndexMethod.SEARCH, first.getMethod());
        assertEquals(Boolean.FALSE, first.getUniqueIndex());
        assertNull(first.getMessage());
        assertEquals("idx_department_name_search", derived.get(1).getIndexName());
        // 'note' is a text column nobody searches — derivation follows intent, not type.
        assertTrue(derived.stream().noneMatch(i -> i.getIndexName().contains("note")));
    }

    @Test
    void noSearchName_fallsBackToTheFieldCalledName() {
        List<SysModelIndex> derived = derive(model("Bank", "bank", List.of(),
                text("code", "code"), text("name", "name")));

        assertEquals(1, derived.size());
        assertEquals("idx_bank_name_search", derived.get(0).getIndexName());
    }

    @Test
    void noSearchNameAndNoNameField_derivesNothing() {
        // ModelManager resolves this model's searchName to ["id"], i.e. it has no text search.
        assertTrue(derive(model("PayRunLine", "pay_run_line", List.of(),
                text("code", "code"))).isEmpty());
    }

    @Test
    void nonStringNameField_isSkippedRatherThanDerived() {
        // ModelManager only asserts STRING for the EXPLICIT branch, so the implicit 'name'
        // fallback can legitimately land on a non-text column.
        ModelSpec spec = new ModelSpec("Shift", "shift", List.of(), false, true,
                List.of(new FieldSpec("name", "name", FieldType.INTEGER, false)));

        assertTrue(derive(spec).isEmpty());
    }

    @Test
    void dynamicField_isSkipped_itHasNoColumnToIndex() {
        ModelSpec spec = new ModelSpec("Report", "report", List.of("name"), false, true,
                List.of(new FieldSpec("name", "name", FieldType.STRING, true)));

        assertTrue(derive(spec).isEmpty());
    }

    @Test
    void projection_derivesNothing_itDoesNotOwnItsTable() {
        ModelSpec spec = new ModelSpec("BirthdayCountdown", "employee", List.of("fullName"), true, true,
                List.of(text("fullName", "full_name")));

        assertTrue(derive(spec).isEmpty());
    }

    @Test
    void nonRdbmsModel_derivesNothing() {
        ModelSpec spec = new ModelSpec("AuditTrail", "audit_trail", List.of("name"), false, false,
                List.of(text("name", "name")));

        assertTrue(derive(spec).isEmpty());
    }

    @Test
    void nameAlreadyClaimedByADeveloperDeclaration_isDropped() {
        ModelSpec spec = model("Department", "department", List.of("name"), text("name", "name"));

        assertTrue(SearchIndexSynthesizer.derive(List.of(spec),
                        List.of(new DeclaredIndex("Other", "IDX_DEPARTMENT_NAME_SEARCH",
                                List.of("whatever"), false))).isEmpty(),
                "index names are case-insensitive identifiers; the collision must still be seen");
    }

    @Test
    void explicitMethodDeclarationOnTheColumn_suppressesDerivation() {
        // The developer took manual control of this column's physical search shape — deriving a
        // second GIN index next to theirs would double the write amplification for nothing.
        ModelSpec spec = model("Employee", "employee", List.of("fullName"),
                text("fullName", "full_name"));
        DeclaredIndex manual = new DeclaredIndex(
                "Employee", "idx_employee_search", List.of("fullName"), true);

        assertTrue(SearchIndexSynthesizer.derive(List.of(spec), List.of(manual)).isEmpty());
    }

    @Test
    void plainBtreeDeclarationOnTheColumn_doesNotSuppressDerivation() {
        // A B-tree on the column serves equality/ordering and cannot answer the search box —
        // the two indexes complement each other, so the derivation must still happen.
        ModelSpec spec = model("Employee", "employee", List.of("fullName"),
                text("fullName", "full_name"));
        DeclaredIndex btree = new DeclaredIndex(
                "Employee", "idx_employee_full_name", List.of("fullName"), false);

        assertEquals(1, SearchIndexSynthesizer.derive(List.of(spec), List.of(btree)).size());
    }

    @Test
    void twoModelsSharingATable_deriveTheNameOnce_ratherThanFailingTheBoot() {
        // ModelManager fails the boot on a duplicate index name. A derived index is never worth
        // that, so the second one is dropped instead.
        List<SysModelIndex> derived = derive(
                model("Employee", "employee", List.of("fullName"), text("fullName", "full_name")),
                model("EmployeeMirror", "employee", List.of("fullName"), text("fullName", "full_name")));

        assertEquals(1, derived.size());
        assertEquals("Employee", derived.get(0).getModelName());
    }

    @Test
    void overlongName_isShortenedDeterministicallyAndStaysWithinTheColumnWidth() {
        String table = "leave_request_rule_employment_type_rel";   // 38 chars, a real worst case
        String column = "counterparty_display_name";

        String first = SearchIndexSynthesizer.deriveIndexName(table, column);
        String second = SearchIndexSynthesizer.deriveIndexName(table, column);

        assertEquals(first, second, "an unstable name would churn the index on every boot");
        assertTrue(first.length() <= 60, "must fit sys_model_index.index_name: " + first);
        assertTrue(first.startsWith("idx_") && first.endsWith("_search"));
        // Both halves survive: a name that kept only the table would collide across its own columns.
        assertTrue(first.contains("leave_request"), first);
        assertTrue(first.contains("counterparty"), first);
    }

    @Test
    void shortenedNamesOfTwoColumnsOnOneLongTable_doNotCollide() {
        String table = "leave_request_rule_employment_type_rel";

        assertNotEquals(
                SearchIndexSynthesizer.deriveIndexName(table, "counterparty_display_name"),
                SearchIndexSynthesizer.deriveIndexName(table, "counterparty_display_label"));
    }

    @Test
    void shortNameIsLeftExactlyAsSpelled() {
        assertEquals("idx_employee_full_name_search",
                SearchIndexSynthesizer.deriveIndexName("employee", "full_name"));
    }
}
