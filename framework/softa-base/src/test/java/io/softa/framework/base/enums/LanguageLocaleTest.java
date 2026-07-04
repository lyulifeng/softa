package io.softa.framework.base.enums;

import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import io.softa.framework.base.utils.DateUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the CLDR-derived locale facts exposed by {@link Language} (JDK is the
 * authoritative CLDR source; nothing is stored). If a JDK upgrade ever shifts
 * one of these values, this test surfaces it explicitly.
 */
class LanguageLocaleTest {

    @Test
    void toLocale_roundTripsTheBcp47Tag() {
        assertEquals("pt-BR", Language.PT_BR.toLocale().toLanguageTag());
        assertEquals(Locale.SIMPLIFIED_CHINESE.toLanguageTag(),
                Language.ZH_CN.toLocale().toLanguageTag());
    }

    @Test
    void separators_comeFromCldr() {
        assertEquals(',', Language.PT_BR.decimalSeparator());
        assertEquals('.', Language.PT_BR.groupingSeparator());
        assertEquals('.', Language.EN_US.decimalSeparator());
        assertEquals(',', Language.EN_US.groupingSeparator());
        // de-CH uses the apostrophe grouping separator — the canonical
        // "same language, different region, different format" case.
        assertEquals('’', Language.DE_CH.groupingSeparator());
    }

    @Test
    void patterns_followPlatformConventions() {
        // MEDIUM date: zh-CN renders with CJK date characters.
        assertTrue(Language.ZH_CN.datePattern().contains("年"),
                "zh-CN medium date pattern should contain 年, got: " + Language.ZH_CN.datePattern());
        // SHORT time: en-US is 12-hour with am/pm marker.
        assertTrue(Language.EN_US.timePattern().contains("a"),
                "en-US short time pattern should contain the am/pm marker, got: "
                        + Language.EN_US.timePattern());
    }

    @Test
    void dateUtils_localizedOverloads() {
        LocalDate date = LocalDate.of(2026, 6, 12);
        assertEquals("2026年6月12日", DateUtils.dateToLocalString(date, Language.ZH_CN));
        assertEquals("Jun 12, 2026", DateUtils.dateToLocalString(date, Language.EN_US));
        assertNull(DateUtils.dateToLocalString(null, Language.EN_US));
    }

    @Test
    void enSg_derivesSingaporeConventionsFromCldr() {
        // British-style separators and day-first date, 12-hour clock.
        assertEquals('.', Language.EN_SG.decimalSeparator());
        assertEquals(',', Language.EN_SG.groupingSeparator());
        assertEquals("12 Jun 2026",
                DateUtils.dateToLocalString(LocalDate.of(2026, 6, 12), Language.EN_SG));
        assertTrue(Language.EN_SG.timePattern().contains("a"),
                "en-SG short time is 12-hour, got: " + Language.EN_SG.timePattern());
    }
}
