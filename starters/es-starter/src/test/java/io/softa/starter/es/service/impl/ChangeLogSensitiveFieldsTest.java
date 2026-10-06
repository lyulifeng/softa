package io.softa.starter.es.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.changelog.message.dto.ChangeLog;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.service.PermissionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A change log read by somebody whose sensitive field sets do not cover every field.
 *
 * <p>A model read masks those fields; the change log carried them through untouched. Anyone who
 * could read a row could therefore read the before and after of its salary amount or account
 * number in its history, edit by edit — the one place a field set did not reach.
 */
class ChangeLogSensitiveFieldsTest {

    private ChangeLogServiceImpl service;
    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        service = new ChangeLogServiceImpl();
        permissionService = mock(PermissionService.class);
        ReflectionTestUtils.setField(service, "permissionService", permissionService);
        when(permissionService.getUserBlockedModelFields("SalaryItem", AccessType.READ))
                .thenReturn(Set.of("amount", "currency"));
        when(permissionService.getUserBlockedModelFields("Person", AccessType.READ))
                .thenReturn(Set.of());
    }

    @Test
    void aBlockedFieldIsTakenOutOfBothSidesOfAnUpdate() {
        ChangeLog log = log("SalaryItem", AccessType.UPDATE,
                Map.of("amount", 5000, "note", "old", "payBasis", "Monthly"),
                Map.of("amount", 6000, "note", "new"));

        List<ChangeLog> visible = service.withoutBlockedFields(List.of(log));

        assertThat(visible).hasSize(1);
        assertThat(visible.getFirst().getDataAfterChange()).containsOnlyKeys("note");
        // Before carries the whole original row, so the blocked field is taken out of it too —
        // leaving it there would hand over the old salary even with the new one hidden.
        assertThat(visible.getFirst().getDataBeforeChange()).containsOnlyKeys("note", "payBasis");
    }

    @Test
    void anUpdateThatTouchedOnlyBlockedFieldsIsNotListedAtAll() {
        // An entry saying "something changed here on this day" is itself what the field set keeps
        // from this reader: it dates a pay rise.
        ChangeLog raise = log("SalaryItem", AccessType.UPDATE,
                Map.of("amount", 5000, "note", "x"), Map.of("amount", 6000));

        assertThat(service.withoutBlockedFields(List.of(raise))).isEmpty();
    }

    @Test
    void aCreationKeepsWhatTheReaderMaySee() {
        ChangeLog created = log("SalaryItem", AccessType.CREATE,
                null, Map.of("amount", 5000, "currency", "SGD", "note", "hired"));

        List<ChangeLog> visible = service.withoutBlockedFields(List.of(created));

        assertThat(visible).hasSize(1);
        assertThat(visible.getFirst().getDataAfterChange()).containsOnlyKeys("note");
    }

    @Test
    void aDeletionKeepsOnlyTheFactThatTheRowWent() {
        // The salary slice is gone from the table; its effective dates would tell this reader a pay
        // period was withdrawn and when — more than the table, showing only what remains, ever did.
        ChangeLog deleted = log("SalaryItem", AccessType.DELETE,
                Map.of("amount", 5000, "effectiveStartDate", "2026-10-01", "note", "raise"), null);

        List<ChangeLog> visible = service.withoutBlockedFields(List.of(deleted));

        assertThat(visible).hasSize(1);
        assertThat(visible.getFirst().getDataBeforeChange()).isEmpty();
    }

    @Test
    void aDeletionIsShownInFullToAReaderWhoseSetsHideNothing() {
        ChangeLog deleted = log("Person", AccessType.DELETE, Map.of("name", "A"), null);

        assertThat(service.withoutBlockedFields(List.of(deleted)).getFirst().getDataBeforeChange())
                .containsEntry("name", "A");
    }

    @Test
    void aModelWithNothingBlockedComesBackAsStored() {
        // Full data access and bypassed checks both answer "nothing blocked"; the logs are untouched.
        ChangeLog log = log("Person", AccessType.UPDATE,
                Map.of("name", "A"), Map.of("name", "B"));

        List<ChangeLog> visible = service.withoutBlockedFields(List.of(log));

        assertThat(visible).containsExactly(log);
        assertThat(visible.getFirst().getDataAfterChange()).containsEntry("name", "B");
    }

    @Test
    void eachLogIsJudgedByItsOwnModel() {
        // A raw search spans models; the person's history keeps its fields while the salary
        // item's loses its own — and each model's sets are asked for once, not once per entry.
        List<ChangeLog> visible = service.withoutBlockedFields(List.of(
                log("Person", AccessType.UPDATE, Map.of("amount", 1), Map.of("amount", 2)),
                log("SalaryItem", AccessType.UPDATE, Map.of("amount", 1), Map.of("amount", 2)),
                log("SalaryItem", AccessType.UPDATE, Map.of("amount", 3), Map.of("amount", 4))));

        assertThat(visible).hasSize(1);
        assertThat(visible.getFirst().getModel()).isEqualTo("Person");
        assertThat(visible.getFirst().getDataAfterChange()).containsEntry("amount", 2);
        verify(permissionService, times(1)).getUserBlockedModelFields("SalaryItem", AccessType.READ);
    }

    private static ChangeLog log(String model, AccessType type,
                                 Map<String, Object> before, Map<String, Object> after) {
        ChangeLog log = new ChangeLog();
        log.setModel(model);
        log.setAccessType(type);
        log.setDataBeforeChange(before == null ? null : new HashMap<>(before));
        log.setDataAfterChange(after == null ? null : new HashMap<>(after));
        return log;
    }
}
