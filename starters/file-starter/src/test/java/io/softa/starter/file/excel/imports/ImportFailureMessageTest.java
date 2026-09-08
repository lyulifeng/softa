package io.softa.starter.file.excel.imports;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.exception.ValidationException;
import io.softa.framework.orm.meta.MetaIndex;
import io.softa.framework.orm.meta.ModelManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What an import failure is allowed to say.
 *
 * <p>The reported leak is the shape asserted first: a unique violation on the employee import
 * arrived in the Failed Reason column as the driver's own text, carrying the INSERT statement, the
 * table, all 44 column names, the constraint name and another tenant's id in plain text. Every
 * assertion here is about the boundary — what reaches a tenant — rather than about the wording,
 * which is why the negative assertions matter as much as the positive ones.
 */
class ImportFailureMessageTest {

    /** The exact exception PostgreSQL produces for the reported case, wrapper text included. */
    private static DuplicateKeyException employeeCodeClash() {
        SQLException driver = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"uk_employee_tenant_code\"\n"
                        + "  Detail: Key (tenant_id, code)=(882561206915698776, UN0001) already exists.",
                "23505");
        return new DuplicateKeyException(
                "PreparedStatementCallback; SQL [INSERT INTO employee (code,work_email,full_name,"
                        + "tenant_id,department_id,legal_entity_id) VALUES (?,?,?,?,?,?)]; "
                        + driver.getMessage(), driver);
    }

    private static MetaIndex index(String message) {
        MetaIndex idx = mock(MetaIndex.class);
        when(idx.getModelName()).thenReturn("Employee");
        when(idx.getIndexFields()).thenReturn(List.of("tenantId", "code"));
        when(idx.getMessage()).thenReturn(message);
        return idx;
    }

    @Test
    void aUniqueViolationBecomesTheIndexsOwnSentence() {
        MetaIndex mapped = index("An employee with this code already exists.");
        try (MockedStatic<ModelManager> registry = Mockito.mockStatic(ModelManager.class)) {
            registry.when(() -> ModelManager.getIndex("uk_employee_tenant_code")).thenReturn(mapped);

            String message = ImportFailureMessage.forRow(employeeCodeClash());

            assertThat(message).isEqualTo("An employee with this code already exists.");
        }
    }

    @Test
    void nothingAboutTheDatabaseSurvivesIntoTheMessage() {
        // The point of the fix, stated as the thing that must not happen. Asserted separately from
        // the wording above so a future change to the sentence cannot quietly re-open the leak.
        MetaIndex mapped = index("An employee with this code already exists.");
        try (MockedStatic<ModelManager> registry = Mockito.mockStatic(ModelManager.class)) {
            registry.when(() -> ModelManager.getIndex("uk_employee_tenant_code")).thenReturn(mapped);

            String message = ImportFailureMessage.forRow(employeeCodeClash());

            assertThat(message)
                    .doesNotContain("INSERT")                    // the statement
                    .doesNotContain("employee (")                // the table and its column list
                    .doesNotContain("work_email")                // any column name
                    .doesNotContain("uk_employee_tenant_code")   // the constraint name
                    .doesNotContain("882561206915698776")        // the tenant id
                    .doesNotContain("PreparedStatementCallback");// the framework's internals
        }
    }

    @Test
    void anUnmappedUniqueViolationStillSaysNothingAboutTheDatabase() {
        // An index the registry does not know — a hand-written constraint, or one whose model is not
        // loaded. The friendly path cannot fire, and the fallback must still not be the driver text.
        try (MockedStatic<ModelManager> registry = Mockito.mockStatic(ModelManager.class)) {
            registry.when(() -> ModelManager.getIndex(Mockito.anyString())).thenReturn(null);

            String message = ImportFailureMessage.forRow(employeeCodeClash());

            assertThat(message).isEqualTo("A record with a duplicate value already exists.");
            assertThat(message).doesNotContain("INSERT").doesNotContain("882561206915698776");
        }
    }

    @Test
    void anotherIntegrityViolationGetsItsOwnValueFreeSentence() {
        DataIntegrityViolationException notNull = new DataIntegrityViolationException(
                "ERROR: null value in column \"full_name\" of relation \"employee\" violates not-null constraint");

        String message = ImportFailureMessage.forRow(notNull);

        assertThat(message).isEqualTo("The operation violates a data integrity constraint.");
        assertThat(message).doesNotContain("full_name").doesNotContain("employee");
    }

    @Test
    void adeliberateBusinessMessagePassesThroughUnchanged() {
        // The other half of the boundary. These sentences are written for this reader and are the
        // only thing that lets someone fix their spreadsheet — withholding them would trade one
        // useless error for another.
        assertThat(ImportFailureMessage.forRow(new ValidationException("The field `Type` is required.")))
                .isEqualTo("The field `Type` is required.");
        assertThat(ImportFailureMessage.forRow(new BusinessException("Cannot find LegalEntity by code=ACME.")))
                .isEqualTo("Cannot find LegalEntity by code=ACME.");
    }

    @Test
    void anUnplannedExceptionSaysNothingOfItsOwn() {
        // An exception nobody wrote for a user: its message is for a developer and can hold
        // anything — a path, a query, a credential someone put in a log line. This is the tier that
        // stops the next leak from being a new bug rather than the same one.
        String message = ImportFailureMessage.forRow(
                new IllegalStateException("jdbc:postgresql://10.0.0.7:5432/hcm?user=root failed"));

        assertThat(message).isEqualTo(
                "This row could not be saved because of an unexpected error. "
                        + "The details are in the server log.");
        assertThat(message).doesNotContain("jdbc").doesNotContain("root");
    }

    @Test
    void aMissingCauseDoesNotBecomeTheWordNull() {
        assertThat(ImportFailureMessage.forRow(null)).startsWith("This row could not be saved");
        assertThat(ImportFailureMessage.forImport(null)).startsWith("The import could not be completed");
    }

    @Test
    void aTaskThatNeverReachedTheRowsIsNotDescribedAsARow() {
        // The download from object storage, a template that would not load: nothing about a row
        // failed, and saying one did sends the operator looking through a spreadsheet for something
        // that is not in it. Same tiers, different last resort.
        String message = ImportFailureMessage.forImport(
                new IllegalStateException("connect to minio://10.0.0.9:9000 refused"));

        assertThat(message).isEqualTo(
                "The import could not be completed because of an unexpected error. "
                        + "The details are in the server log.");
        assertThat(message).doesNotContain("minio").doesNotContain("10.0.0.9");
    }
}
