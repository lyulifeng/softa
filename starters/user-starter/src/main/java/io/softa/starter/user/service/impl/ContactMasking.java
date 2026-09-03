package io.softa.starter.user.service.impl;

import org.apache.commons.lang3.StringUtils;

/**
 * Masks the contact details shown on the /join screens (PRD §3.1).
 *
 * <p>The point is confirmation, not disclosure: the invitee needs to recognise their own address
 * to know the invitation reached the right person, while a link that leaked must not hand a
 * stranger a working phone number or email. Enough to recognise, not enough to reuse.
 *
 * <h3>Why not the framework's own masking</h3>
 * {@code MaskingType} + {@code MaskingProcessor} exist and are the right long-term home for these
 * rules. Two things kept this local, and both are fixable rather than fundamental:
 *
 * <ul>
 *   <li><b>The rules do not match.</b> {@code MaskingType.EMAIL} keeps the first four characters
 *       and drops the domain ({@code alic****}), which is backwards for a screen whose job is
 *       recognition: it leaks more of the identifying local part and removes the one part — the
 *       company's domain — that tells the invitee this is theirs. And
 *       {@code MaskingProcessor.maskingPhoneNumber} does not do what its javadoc says: the doc
 *       promises "retain only the last 4", the code retains everything BUT the last four
 *       ({@code +8613800138000} → {@code +861380013****}, ten digits of fourteen). Neither shape
 *       is safe under the threat model here, which is a link forwarded to the wrong person.</li>
 *   <li><b>The mechanism does not reach.</b> {@code MaskingProcessor} is a read-pipeline processor:
 *       it needs a {@code MetaField} and an {@code AccessType}, and it fires on model rows when
 *       {@code Context.isDataMask()} is set. The /join responses are DTOs assembled by hand from an
 *       invitation, so there is no field metadata to hang a {@code maskingType} on. Only the static
 *       helpers are callable, and those are the ones whose rules are wrong.</li>
 * </ul>
 *
 * <p>The framework route is to lift the pure rules out of {@code MaskingProcessor} into a utility
 * both the pipeline and hand-built DTOs can call, correct {@code EMAIL}, align
 * {@code PHONE_NUMBER} with its own javadoc, and add the international shape this class
 * implements. All three are free today: no field anywhere declares {@code EMAIL} or
 * {@code PHONE_NUMBER}. Deliberately NOT done in this release — it is a framework change, and
 * these PRs were split so none of them depends on an unmerged framework PR. When it lands, this
 * class goes away and the two call sites in {@code UserInvitationServiceImpl} move to it.
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
