package io.softa.framework.web.task.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.SystemException;
import io.softa.framework.base.security.EncryptUtils;
import io.softa.framework.base.security.algorithm.AESEncryption;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.web.task.params.PlaintextToCiphertextParams;

class PlaintextToCiphertextHandlerTest {

    private static final String MODEL = "EmpInfo";
    private static final String PASSWORD = "right-password";

    /** Produced by encrypt("S1234567D", "right-password"). */
    private static final String CIPHERTEXT = "pTOZ/yOb8+LBdxFTz02oD7nyD9VMnXxbWRLEkW3oRJg=";

    private final PlaintextToCiphertextHandler handler = new PlaintextToCiphertextHandler();

    @SuppressWarnings("rawtypes")
    private ModelService modelService;
    private MockedStatic<ModelManager> modelManager;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(EncryptUtils.class, "encryptor", new AESEncryption());
        ReflectionTestUtils.setField(EncryptUtils.class, "password", PASSWORD);

        modelService = Mockito.mock(ModelService.class);
        ReflectionTestUtils.setField(handler, "modelService", modelService);

        modelManager = mockStatic(ModelManager.class);
        modelManager.when(() -> ModelManager.isTimelineModel(MODEL)).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        modelManager.close();
    }

    @SuppressWarnings("unchecked")
    private void givenRows(List<Map<String, Object>> rows) {
        when(modelService.searchList(anyString(), any())).thenReturn(rows);
    }

    private static PlaintextToCiphertextParams params(String... fields) {
        PlaintextToCiphertextParams taskParams = new PlaintextToCiphertextParams();
        taskParams.setModel(MODEL);
        taskParams.setFields(new LinkedHashSet<>(List.of(fields)));
        taskParams.setIds(Set.<Serializable>of(11L, 12L));
        return taskParams;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> captureUpdatedRows() {
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(modelService).updateList(eq(MODEL), captor.capture());
        return captor.getValue();
    }

    /**
     * A row already encrypted in one field and plaintext in another must be submitted carrying the
     * plaintext field alone: the other field's stored ciphertext would be encrypted a second time.
     */
    @Test
    void execute_submitsOnlyTheFieldBeingEncrypted() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 11L);
        row.put("nric", "hello@example.com");
        row.put("bankAccount", CIPHERTEXT);
        givenRows(new ArrayList<>(List.of(row)));

        handler.execute(params("nric", "bankAccount"));

        assertThat(captureUpdatedRows()).singleElement().satisfies(update ->
                assertThat(update).containsOnlyKeys("id", "nric")
                        .containsEntry("nric", "hello@example.com"));
    }

    /**
     * A row whose field is null or empty holds nothing to encrypt and must not be rewritten.
     */
    @Test
    void execute_ignoresRowsWithNoValue() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("id", 11L);
        empty.put("nric", null);
        Map<String, Object> blank = new LinkedHashMap<>();
        blank.put("id", 12L);
        blank.put("nric", "");
        Map<String, Object> plaintext = new LinkedHashMap<>();
        plaintext.put("id", 13L);
        plaintext.put("nric", "1234567890123456");
        givenRows(new ArrayList<>(List.of(empty, blank, plaintext)));

        handler.execute(params("nric"));

        assertThat(captureUpdatedRows()).extracting(update -> update.get("id")).containsExactly(13L);
    }

    @Test
    void execute_writesNothing_whenEveryRowIsAlreadyEncrypted() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 11L);
        row.put("nric", CIPHERTEXT);
        givenRows(new ArrayList<>(List.of(row)));

        handler.execute(params("nric"));

        verify(modelService, never()).updateList(anyString(), any());
    }

    /**
     * Encrypting under a key the existing rows were not written with would leave the column half
     * readable, so a mismatch stops the task before the first write.
     */
    @Test
    void execute_failsBeforeWriting_whenTheKeyDoesNotMatch() {
        ReflectionTestUtils.setField(EncryptUtils.class, "password", "wrong-password");
        Map<String, Object> encrypted = new LinkedHashMap<>();
        encrypted.put("id", 11L);
        encrypted.put("nric", CIPHERTEXT);
        Map<String, Object> plaintext = new LinkedHashMap<>();
        plaintext.put("id", 12L);
        plaintext.put("nric", "hello@example.com");
        givenRows(new ArrayList<>(List.of(encrypted, plaintext)));

        assertThatThrownBy(() -> handler.execute(params("nric")))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("security.encryption.password");

        verify(modelService, never()).updateList(anyString(), any());
    }
}
