package io.softa.starter.message.mail.support;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.softa.framework.web.dto.OnChangeResponse;
import io.softa.framework.web.onchange.OnChangeContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailTemplateBodyModeOnChangeHandlerTest {

    private final MailTemplateBodyModeOnChangeHandler handler = new MailTemplateBodyModeOnChangeHandler();

    private OnChangeResponse fire(String newMode, String bodyHtml, String bodyText) {
        Map<String, Object> companions = new java.util.HashMap<>();
        companions.put("bodyHtml", bodyHtml);
        companions.put("bodyText", bodyText);
        return handler.onChange(new OnChangeContext("MailTemplate", "bodyMode", "1", newMode, companions));
    }

    @Test
    void toAuthored_withHtmlOnly_prefillsDerivedDraft() {
        OnChangeResponse r = fire("HTML_WITH_AUTHORED_PLAIN", "<p>Hello <b>world</b></p>", null);

        String draft = (String) r.getValues().get("bodyText");
        assertTrue(draft.contains("Hello"));
        assertFalse(draft.contains("<"));
        assertFalse(r.getValues().containsKey("bodyHtml"), "master column must stay untouched");
    }

    @Test
    void toAuthored_withBothPopulated_neverOverwrites() {
        assertNull(fire("HTML_WITH_AUTHORED_PLAIN", "<p>html</p>", "authored text"));
    }

    @Test
    void toPlain_migratesHtmlIntoTextAndClearsHtml() {
        OnChangeResponse r = fire("PLAIN", "<p>Hello</p>", null);

        assertTrue(((String) r.getValues().get("bodyText")).contains("Hello"));
        assertTrue(r.getValues().containsKey("bodyHtml"));
        assertNull(r.getValues().get("bodyHtml"), "PLAIN sends no HTML part — column cleared");
    }

    @Test
    void toHtml_fromPlain_prefillsEscapedParagraphsAndClearsText() {
        OnChangeResponse r = fire("HTML", null, "1 < 2 & so\n\nsecond\nline");

        assertEquals("<p>1 &lt; 2 &amp; so</p><p>second<br>line</p>", r.getValues().get("bodyHtml"));
        assertTrue(r.getValues().containsKey("bodyText"));
        assertNull(r.getValues().get("bodyText"));
    }

    @Test
    void toDerived_withBothPopulated_onlyClearsText() {
        OnChangeResponse r = fire("HTML_WITH_DERIVED_PLAIN", "<p>html</p>", "stale authored");

        assertTrue(r.getValues().containsKey("bodyText"));
        assertNull(r.getValues().get("bodyText"), "derived mode regenerates plain at send time");
        assertFalse(r.getValues().containsKey("bodyHtml"));
    }

    @Test
    void unknownMode_orNothingToDo_returnsNull() {
        assertNull(fire("NOT_A_MODE", "<p>x</p>", null));
        assertNull(fire(null, "<p>x</p>", null));
        assertNull(fire("HTML", "<p>already html</p>", null), "target populated, source empty — no-op");
    }
}
