package io.softa.starter.user.service.impl;

import org.apache.commons.lang3.StringUtils;

/**
 * Masks the contact details shown on the /join screens (PRD §3.1).
 *
 * <p>The point is confirmation, not disclosure: the invitee needs to recognise their own address
 * to know the invitation reached the right person, while a link that leaked must not hand a
 * stranger a working phone number or email. Enough to recognise, not enough to reuse.
 */
final class ContactMasking {

    private ContactMasking() {
    }

    /**
     * {@code +8613800138000} → {@code +861****8000} — first three and last four around a fixed
     * mask, with the leading {@code +} kept so an international number still reads as one. Numbers
     * shorter than 7 digits keep only their last 3, since masking a short number to a fixed shape
     * would reveal proportionally more.
     */
    static String mobile(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String digits = value.replaceAll("[^0-9+]", "");
        String prefix = digits.startsWith("+") ? "+" : "";
        String national = digits.startsWith("+") ? digits.substring(1) : digits;
        if (national.length() < 7) {
            int keep = Math.min(3, national.length());
            return prefix + "****" + national.substring(national.length() - keep);
        }
        return prefix + national.substring(0, 3) + "****" + national.substring(national.length() - 4);
    }

    /** {@code alice@acme.com} → {@code a***@acme.com}; the domain stays so the company is evident. */
    static String email(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }
}
