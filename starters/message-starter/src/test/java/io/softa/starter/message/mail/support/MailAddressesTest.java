package io.softa.starter.message.mail.support;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailAddressesTest {

    @Test
    void mailboxes_acceptBareAndDisplayNameForms() {
        assertDoesNotThrow(() -> MailAddresses.requireValidMailboxes("to",
                List.of("alice@example.com", "Bob Smith <bob@example.com>")));
        assertDoesNotThrow(() -> MailAddresses.requireValidMailboxes("cc", null));
        assertDoesNotThrow(() -> MailAddresses.requireValidMailboxes("cc", List.of()));
    }

    @Test
    void mailboxes_rejectMalformed_namingFieldAndEntry() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> MailAddresses.requireValidMailboxes("to",
                        List.of("alice@example.com", "not-an-email")));
        assertTrue(ex.getMessage().contains("to"));
        assertTrue(ex.getMessage().contains("not-an-email"));
    }

    @Test
    void mailboxes_rejectEmbeddedListInOneEntry() {
        // One entry must be ONE mailbox — a smuggled list fails like the
        // transport would fail it.
        assertThrows(BusinessException.class,
                () -> MailAddresses.requireValidMailboxes("to",
                        List.of("a@example.com, b@example.com")));
    }

    @Test
    void addressList_acceptsMultipleCommaSeparated_withDisplayNames() {
        assertDoesNotThrow(() -> MailAddresses.requireValidAddressList("replyTo",
                "support@example.com, Ops Team <ops@example.com>"));
        assertDoesNotThrow(() -> MailAddresses.requireValidAddressList("replyTo", null));
        assertDoesNotThrow(() -> MailAddresses.requireValidAddressList("replyTo", "  "));
    }

    @Test
    void addressList_rejectsMalformed() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> MailAddresses.requireValidAddressList("replyTo", "support@, broken"));
        assertTrue(ex.getMessage().contains("replyTo"));
    }

    @Test
    void parseAddressList_yieldsOneAddressPerMailbox() throws Exception {
        assertTrue(MailAddresses.parseAddressList("a@example.com, B <b@example.com>").length == 2);
    }

    @Test
    void normalize_convertsSemicolonsAndNewlinesToCommas() {
        assertEquals("a@x.com, b@y.com, c@z.com",
                MailAddresses.normalizeAddressList("a@x.com; b@y.com\nc@z.com"));
        assertDoesNotThrow(() -> MailAddresses.requireValidAddressList("replyTo",
                MailAddresses.normalizeAddressList("a@x.com;b@y.com")));
    }

    @Test
    void normalize_passesPureCommaSyntaxThroughUntouched() {
        // Quoted display-names with embedded commas must survive — a naive
        // split would shred them, so comma-only input is never rewritten.
        String quoted = "\"Smith, John\" <j@x.com>, ops@x.com";
        assertEquals(quoted, MailAddresses.normalizeAddressList(quoted));
        assertDoesNotThrow(() -> MailAddresses.requireValidAddressList("replyTo", quoted));
    }
}
