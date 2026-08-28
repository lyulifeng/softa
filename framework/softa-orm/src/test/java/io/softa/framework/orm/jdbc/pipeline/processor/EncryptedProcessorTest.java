package io.softa.framework.orm.jdbc.pipeline.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.SystemException;
import io.softa.framework.base.security.EncryptUtils;
import io.softa.framework.base.security.algorithm.AESEncryption;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.meta.MetaField;

class EncryptedProcessorTest {

    private static final String PASSWORD = "right-password";

    /** Produced by encrypt("S1234567D", "right-password"). */
    private static final String CIPHERTEXT = "pTOZ/yOb8+LBdxFTz02oD7nyD9VMnXxbWRLEkW3oRJg=";

    private final Logger logger = (Logger) LoggerFactory.getLogger(EncryptedProcessor.class);
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    private EncryptedProcessor processor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(EncryptUtils.class, "encryptor", new AESEncryption());
        ReflectionTestUtils.setField(EncryptUtils.class, "password", PASSWORD);

        MetaField metaField = new MetaField();
        ReflectionTestUtils.setField(metaField, "modelName", "EmpInfo");
        ReflectionTestUtils.setField(metaField, "fieldName", "nric");
        processor = new EncryptedProcessor(metaField, AccessType.READ);

        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    private static Map<String, Object> row(Object id, String nric) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("nric", nric);
        return row;
    }

    /**
     * The rows that hold plaintext keep their stored value, and a single warning names the model, the
     * field and the ids to repair - the algorithm itself only ever sees an opaque value.
     */
    @Test
    void batchProcessOutputRows_namesModelFieldAndRowIds_whenSomeRowsHoldPlaintext() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row(11L, "1234567890123456"),         // valid Base64, decodes to 12 bytes
                row(12L, CIPHERTEXT),
                row(13L, "ABCDEFGHIJKLMNOPQRSTUVWX"), // valid Base64, decodes to 18 bytes
                row(14L, "hello@example.com")));      // not Base64

        processor.batchProcessOutputRows(rows);

        assertThat(rows).extracting(r -> r.get("nric")).containsExactly(
                "1234567890123456", "S1234567D", "ABCDEFGHIJKLMNOPQRSTUVWX", "hello@example.com");

        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("EmpInfo", "nric", "3 of 4 rows", "[11, 13, 14]");
        });
    }

    @Test
    void batchProcessOutputRows_isQuiet_whenEveryRowDecrypts() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(row(12L, CIPHERTEXT)));

        processor.batchProcessOutputRows(rows);

        assertThat(rows.getFirst()).containsEntry("nric", "S1234567D");
        assertThat(logAppender.list).isEmpty();
    }

    @Test
    void batchProcessOutputRows_failsFastNamingTheField_whenTheKeyDoesNotMatch() {
        ReflectionTestUtils.setField(EncryptUtils.class, "password", "wrong-password");
        List<Map<String, Object>> rows = new ArrayList<>(List.of(row(12L, CIPHERTEXT)));

        assertThatThrownBy(() -> processor.batchProcessOutputRows(rows))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("EmpInfo")
                .hasMessageContaining("nric")
                .cause().hasMessageContaining("security.encryption.password");
    }
}
