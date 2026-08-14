package io.softa.starter.message.mail.support;

import java.util.Arrays;
import java.util.List;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import io.softa.framework.base.exception.BusinessException;

/**
 * Accept-time address validation and Reply-To list parsing.
 *
 * <p>Validation runs at acceptance so a bad address is a synchronous 4xx
 * naming the field and the offending entry — not an asynchronous SMTP failure
 * discovered later on the send record. The rules mirror the transport
 * exactly: each {@code to} / {@code cc} / {@code bcc} entry is ONE strict
 * RFC822 mailbox (display-name form {@code Alice <a@x.com>} allowed), and
 * {@code replyTo} is an RFC822 <i>address-list</i> — one or more mailboxes,
 * comma-separated.
 */
public final class MailAddresses {

    private MailAddresses() {
    }

    /** Each entry must be exactly one strict RFC822 mailbox. */
    public static void requireValidMailboxes(String field, List<String> addresses) {
        if (CollectionUtils.isEmpty(addresses)) {
            return;
        }
        for (String address : addresses) {
            try {
                // The single-address constructor rejects embedded lists, so an
                // entry like "a@x.com, b@y.com" fails here — matching what the
                // transport would do with it.
                new InternetAddress(address, true).validate();
            } catch (AddressException e) {
                throw new BusinessException(
                        "Mail send rejected: invalid {0} address ''{1}''", field, address);
            }
        }
    }

    /** Strict RFC822 address-list — one or more mailboxes, comma-separated. */
    public static void requireValidAddressList(String field, String addressList) {
        if (!StringUtils.hasText(addressList)) {
            return;
        }
        try {
            InternetAddress[] parsed = InternetAddress.parse(addressList, true);
            if (parsed.length == 0) {
                throw new AddressException("empty address list");
            }
            for (InternetAddress address : parsed) {
                address.validate();
            }
        } catch (AddressException e) {
            throw new BusinessException(
                    "Mail send rejected: invalid {0} address list ''{1}''", field, addressList);
        }
    }

    /** Parse an (already validated) address-list for the transport. */
    public static InternetAddress[] parseAddressList(String addressList) throws AddressException {
        return InternetAddress.parse(addressList, true);
    }

    /**
     * Author-friendly separators → RFC822: semicolons and newlines become
     * commas, so the writing rules match the recipient inputs everywhere.
     * Input that already uses pure comma syntax passes through UNTOUCHED —
     * that keeps quoted display-names with embedded commas
     * ({@code "Smith, John" <j@x.com>}) intact, which a naive split would
     * shred. Runs at acceptance, before validation; the record stores the
     * normalized form so retries replay it verbatim.
     */
    public static String normalizeAddressList(String raw) {
        if (!StringUtils.hasText(raw) || (raw.indexOf(';') < 0
                && raw.indexOf('\n') < 0 && raw.indexOf('\r') < 0)) {
            return raw;
        }
        List<String> parts = Arrays.stream(raw.split("[,;\r\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return String.join(", ", parts);
    }
}
