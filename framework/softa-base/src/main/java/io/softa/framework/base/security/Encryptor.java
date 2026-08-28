package io.softa.framework.base.security;

import java.util.Map;

public interface Encryptor {

    /**
     * Encrypts the provided data.
     *
     * @param plaintext     The data to encrypt.
     * @param password The password used to generate the encryption key.
     * @return The Base64-encoded encrypted data.
     */
    String encrypt(String plaintext, String password) throws Exception;

    /**
     * Batch encrypts the provided data.
     * @param plaintextIndexMap Plain text index map
     * @param password The password used to generate the encryption key.
     * @return Index Map of the Base64-encoded encrypted data.
     */
    Map<Integer, String> encrypt(Map<Integer, String> plaintextIndexMap, String password) throws Exception;

    /**
     * Tells whether a stored value is ciphertext of this algorithm, without decrypting it.
     * Lets a caller skip values that are already encrypted, and find plaintext left in an encrypted
     * column, with no key derivation involved.
     *
     * <p>The default is deliberately conservative: an implementation that cannot recognise its own
     * output reports every value as ciphertext, so a repair job does nothing rather than encrypting
     * a value a second time. Override it whenever the output has a recognisable layout.
     *
     * @param value The stored value.
     * @return True if the value is ciphertext of this algorithm.
     */
    default boolean isCiphertext(String value) {
        return true;
    }

    /**
     * Decrypts the provided data.
     * Return original string if it cannot be decrypted
     *
     * @param ciphertext      The encrypted data.
     * @param password        The password used to generate the decryption key.
     * @return The decrypted data as a string.
     */
    String decrypt(String ciphertext, String password) throws Exception ;

    /**
     * Batch decrypts the provided data.
     * Discard the data that cannot be decrypted.
     *
     * @param ciphertextIndexMap Cipher text index map
     * @param password The password used to generate the encryption key.
     * @return Index Map of the Base64-encoded decrypted data.
     */
    Map<Integer, String> decrypt(Map<Integer, String> ciphertextIndexMap, String password) throws Exception;

}
