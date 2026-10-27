package io.softa.starter.metadata.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A relation onto a model the caller can read nothing of is withheld with that model's fields: it could
 * only offer a column, an export field and a filter that come back empty.
 */
class WithheldRelationTest {

    /** An employee's bank account holds nothing but sensitive fields and plumbing. */
    private static final List<MetaField> BANK_ACCOUNT = List.of(
            field("id", FieldType.LONG, null),
            field("tenantId", FieldType.LONG, null),
            field("createdTime", FieldType.DATE_TIME, null),
            field("employeeId", FieldType.MANY_TO_ONE, "Employee"),
            field("accountNumber", FieldType.STRING, null),
            field("bank", FieldType.MANY_TO_ONE, "Bank"));

    @Test
    void aRelationOntoAModelWhoseEveryFieldIsWithheldIsWithheldToo() {
        Map<String, Set<String>> withheld = Map.of("BankAccount", Set.of("accountNumber", "bank"));
        try (MockedStatic<ModelManager> models = models()) {
            assertThat(isWithheld("bankAccountId", FieldType.ONE_TO_ONE, "BankAccount", withheld)).isTrue();
            assertThat(isWithheld("departmentId", FieldType.MANY_TO_ONE, "Department", withheld)).isFalse();
            assertThat(isWithheld("name", FieldType.STRING, null, withheld)).isFalse();
        }
    }

    @Test
    void aRelationOntoAModelWithAFieldLeftToReadStays() {
        Map<String, Set<String>> withheld = Map.of("BankAccount", Set.of("accountNumber"));
        try (MockedStatic<ModelManager> models = models()) {
            assertThat(isWithheld("bankAccountId", FieldType.ONE_TO_ONE, "BankAccount", withheld)).isFalse();
        }
    }

    @Test
    void theModelsOwnWithheldFieldsStayWithheld() {
        Map<String, Set<String>> withheld = Map.of("Employee", Set.of("name"));
        assertThat(isWithheld("name", FieldType.STRING, null, withheld)).isTrue();
    }

    /** The back-reference names the record the caller came from; it does not count as something to read. */
    @Test
    void theBackReferenceOntoTheOwnerIsPlumbing() {
        Map<String, Set<String>> withheld = Map.of("BankAccount", Set.of("accountNumber", "bank"));
        try (MockedStatic<ModelManager> models = models()) {
            assertThat(MetadataServiceImpl.nothingLeftToRead("BankAccount", "Employee", withheld)).isTrue();
            // Reached from another model, the employee it points at is something to read.
            assertThat(MetadataServiceImpl.nothingLeftToRead("BankAccount", "Payslip", withheld)).isFalse();
        }
    }

    /** A model whose fields are not known has not been seen to withhold anything: the relation stays. */
    @Test
    void aModelWithNoKnownFieldsIsNotEmptyToTheCaller() {
        Map<String, Set<String>> withheld = Map.of("Unknown", Set.of("secret"));
        try (MockedStatic<ModelManager> models = models()) {
            models.when(() -> ModelManager.getModelFields("Unknown")).thenReturn(List.of());
            assertThat(isWithheld("unknownId", FieldType.MANY_TO_ONE, "Unknown", withheld)).isFalse();
        }
    }

    /** A related model with nothing withheld is never looked into. */
    @Test
    void nothingWithheldLeavesEveryRelation() {
        assertThat(isWithheld("bankAccountId", FieldType.ONE_TO_ONE, "BankAccount", Map.of())).isFalse();
    }

    private static boolean isWithheld(String field, FieldType type, String related, Map<String, Set<String>> withheld) {
        return MetadataServiceImpl.isWithheld("Employee", field, type, related, withheld);
    }

    private static MockedStatic<ModelManager> models() {
        MockedStatic<ModelManager> models = Mockito.mockStatic(ModelManager.class);
        models.when(() -> ModelManager.getModelFields("BankAccount")).thenReturn(BANK_ACCOUNT);
        return models;
    }

    private static MetaField field(String name, FieldType type, String relatedModel) {
        MetaField field = mock(MetaField.class);
        when(field.getFieldName()).thenReturn(name);
        when(field.getFieldType()).thenReturn(type);
        when(field.getRelatedModel()).thenReturn(relatedModel);
        return field;
    }
}
