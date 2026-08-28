package io.softa.framework.base.security.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import io.softa.framework.base.exception.SystemException;

class AESEncryptionTest {

    private static final String PASSWORD = "right-password";

    /** Produced by encrypt("S1234567D", "right-password"); fixed so the wrong-password case is deterministic. */
    private static final String CIPHERTEXT = "pTOZ/yOb8+LBdxFTz02oD7nyD9VMnXxbWRLEkW3oRJg=";

    private final AESEncryption encryption = new AESEncryption();

    @Test
    void encrypt_thenDecrypt_roundTrips() throws Exception {
        String plaintext = "S1234567D";
        String ciphertext = encryption.encrypt(plaintext, PASSWORD);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(encryption.decrypt(ciphertext, PASSWORD)).isEqualTo(plaintext);
        assertThat(encryption.decrypt(CIPHERTEXT, PASSWORD)).isEqualTo(plaintext);
    }

    @Test
    void encryptAndDecrypt_passThroughNullAndEmpty() throws Exception {
        assertThat(encryption.encrypt((String) null, PASSWORD)).isNull();
        assertThat(encryption.encrypt("", PASSWORD)).isEmpty();
        assertThat(encryption.decrypt((String) null, PASSWORD)).isNull();
        assertThat(encryption.decrypt("", PASSWORD)).isEmpty();
    }

    /**
     * The layout test the repair tooling classifies stored values with: it must never call a plaintext
     * value ciphertext, or that row is left unencrypted for good; and never call ciphertext plaintext,
     * or that row is encrypted a second time.
     */
    @Test
    void isCiphertext_recognisesOnlyValuesProducedByEncrypt() throws Exception {
        assertThat(encryption.isCiphertext(CIPHERTEXT)).isTrue();
        assertThat(encryption.isCiphertext(encryption.encrypt("S1234567D", PASSWORD))).isTrue();
        assertThat(encryption.isCiphertext(encryption.encrypt("x".repeat(500), PASSWORD))).isTrue();
        // Encrypted under another key: still ciphertext, and must not be re-encrypted
        assertThat(encryption.isCiphertext(encryption.encrypt("S1234567D", "other-password"))).isTrue();

        assertThat(encryption.isCiphertext("1234567890123456")).isFalse();
        assertThat(encryption.isCiphertext("ABCDEFGHIJKLMNOPQRSTUVWX")).isFalse();
        assertThat(encryption.isCiphertext("hello@example.com")).isFalse();
        assertThat(encryption.isCiphertext("short")).isFalse();
        assertThat(encryption.isCiphertext("")).isFalse();
        assertThat(encryption.isCiphertext(null)).isFalse();
    }

    /**
     * A plaintext value stored in an encrypted column must be returned unchanged, whatever its length.
     * "1234567890123456" decodes to 12 bytes - shorter than the IV - and "ABCDEFGHIJKLMNOPQRSTUVWX"
     * to 18 bytes, leaving 2 bytes that are not a whole AES block.
     */
    @Test
    void decrypt_returnsValueUnchanged_whenColumnHoldsPlaintext() throws Exception {
        for (String plaintext : new String[] {
                "1234567890123456",         // valid Base64, decodes to 12 bytes
                "ABCDEFGHIJKLMNOPQRSTUVWX", // valid Base64, decodes to 18 bytes
                "hello@example.com",        // not Base64
                "short"                     // not Base64
        }) {
            assertThat(encryption.decrypt(plaintext, PASSWORD)).isEqualTo(plaintext);
        }
    }

    /**
     * Well-formed ciphertext that does not decrypt is a key mismatch, not stray plaintext: returning it
     * unchanged would hand a Base64 blob to the caller and encrypt it a second time on the next save.
     */
    @Test
    void decrypt_failsFast_whenPasswordDoesNotMatch() {
        assertThatThrownBy(() -> encryption.decrypt(CIPHERTEXT, "wrong-password"))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("security.encryption.password");

        Map<Integer, String> ciphertextMap = Map.of(0, CIPHERTEXT);
        assertThatThrownBy(() -> encryption.decrypt(ciphertextMap, "wrong-password"))
                .isInstanceOf(SystemException.class);
    }

    /**
     * One undecryptable value must not fail the whole batch: the decryptable rows are returned and
     * the others are left out of the map, so the caller keeps their stored value.
     */
    @Test
    void batchDecrypt_keepsDecryptableValues_whenOthersAreNotCiphertext() {
        Map<Integer, String> ciphertextMap = new LinkedHashMap<>();
        ciphertextMap.put(0, "1234567890123456");
        ciphertextMap.put(1, CIPHERTEXT);
        ciphertextMap.put(2, "ABCDEFGHIJKLMNOPQRSTUVWX");
        ciphertextMap.put(3, "hello@example.com");

        assertThatCode(() -> encryption.decrypt(ciphertextMap, PASSWORD)).doesNotThrowAnyException();
        assertThat(encryption.decrypt(ciphertextMap, PASSWORD)).containsExactly(Map.entry(1, "S1234567D"));
    }
}
