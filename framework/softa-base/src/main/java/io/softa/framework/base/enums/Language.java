package io.softa.framework.base.enums;

import java.text.DecimalFormatSymbols;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;
import io.softa.framework.base.utils.Assert;

/**
 * Language enum, encoding in ISO 639-1 format, using `-` as separator.
 */
@Slf4j
@Getter
@AllArgsConstructor
@OptionSet(description = "ISO 639-1 language tags with '-' separator")
public enum Language {
    @OptionItem(label = "Amharic / አምሃርኛ")
    AM_ET("am-ET"),
    @OptionItem(label = "Arabic / الْعَرَبيّة")
    AR_001("ar-001"),
    @OptionItem(label = "Arabic (Syria) / الْعَرَبيّة")
    AR_SY("ar-SY"),
    @OptionItem(label = "Azerbaijani / Azərbaycanca")
    AZ_AZ("az-AZ"),
    @OptionItem(label = "Bulgarian / български език")
    BG_BG("bg-BG"),
    @OptionItem(label = "Bengali / বাংলা")
    BN_IN("bn-IN"),
    @OptionItem(label = "Bosnian / bosanski jezik")
    BS_BA("bs-BA"),
    @OptionItem(label = "Catalan / Català")
    CA_ES("ca-ES"),
    @OptionItem(label = "Czech / Čeština")
    CS_CZ("cs-CZ"),
    @OptionItem(label = "Danish / Dansk")
    DA_DK("da-DK"),
    @OptionItem(label = "German (CH) / Deutsch (CH)")
    DE_CH("de-CH"),
    @OptionItem(label = "German / Deutsch")
    DE_DE("de-DE"),
    @OptionItem(label = "Greek / Ελληνικά")
    EL_GR("el-GR"),
    @OptionItem(label = "English (AU)")
    EN_AU("en-AU"),
    @OptionItem(label = "English (CA)")
    EN_CA("en-CA"),
    @OptionItem(label = "English (UK)")
    EN_GB("en-GB"),
    @OptionItem(label = "English (IN)")
    EN_IN("en-IN"),
    @OptionItem(label = "English (NZ)")
    EN_NZ("en-NZ"),
    @OptionItem(label = "English (SG)")
    EN_SG("en-SG"),
    @OptionItem(label = "English (US)")
    EN_US("en-US"),
    @OptionItem(label = "Spanish (Latin America) / Español (América Latina)")
    ES_419("es-419"),
    @OptionItem(label = "Spanish (AR) / Español (AR)")
    ES_AR("es-AR"),
    @OptionItem(label = "Spanish (BO) / Español (BO)")
    ES_BO("es-BO"),
    @OptionItem(label = "Spanish (CL) / Español (CL)")
    ES_CL("es-CL"),
    @OptionItem(label = "Spanish (CO) / Español (CO)")
    ES_CO("es-CO"),
    @OptionItem(label = "Spanish (CR) / Español (CR)")
    ES_CR("es-CR"),
    @OptionItem(label = "Spanish (DO) / Español (DO)")
    ES_DO("es-DO"),
    @OptionItem(label = "Spanish (EC) / Español (EC)")
    ES_EC("es-EC"),
    @OptionItem(label = "Spanish / Español")
    ES_ES("es-ES"),
    @OptionItem(label = "Spanish (GT) / Español (GT)")
    ES_GT("es-GT"),
    @OptionItem(label = "Spanish (MX) / Español (MX)")
    ES_MX("es-MX"),
    @OptionItem(label = "Spanish (PA) / Español (PA)")
    ES_PA("es-PA"),
    @OptionItem(label = "Spanish (PE) / Español (PE)")
    ES_PE("es-PE"),
    @OptionItem(label = "Spanish (PY) / Español (PY)")
    ES_PY("es-PY"),
    @OptionItem(label = "Spanish (UY) / Español (UY)")
    ES_UY("es-UY"),
    @OptionItem(label = "Spanish (VE) / Español (VE)")
    ES_VE("es-VE"),
    @OptionItem(label = "Estonian / Eesti keel")
    ET_EE("et-EE"),
    @OptionItem(label = "Basque / Euskara")
    EU_ES("eu-ES"),
    @OptionItem(label = "Persian / فارسی")
    FA_IR("fa-IR"),
    @OptionItem(label = "Finnish / Suomi")
    FI_FI("fi-FI"),
    @OptionItem(label = "French (BE) / Français (BE)")
    FR_BE("fr-BE"),
    @OptionItem(label = "French (CA) / Français (CA)")
    FR_CA("fr-CA"),
    @OptionItem(label = "French (CH) / Français (CH)")
    FR_CH("fr-CH"),
    @OptionItem(label = "French / Français")
    FR_FR("fr-FR"),
    @OptionItem(label = "Galician / Galego")
    GL_ES("gl-ES"),
    @OptionItem(label = "Gujarati / ગુજરાતી")
    GU_IN("gu-IN"),
    @OptionItem(label = "Hebrew / עִבְרִי")
    HE_IL("he-IL"),
    @OptionItem(label = "Hindi / हिंदी")
    HI_IN("hi-IN"),
    @OptionItem(label = "Croatian / hrvatski jezik")
    HR_HR("hr-HR"),
    @OptionItem(label = "Hungarian / Magyar")
    HU_HU("hu-HU"),
    @OptionItem(label = "Indonesian / Bahasa Indonesia")
    ID_ID("id-ID"),
    @OptionItem(label = "Italian / Italiano")
    IT_IT("it-IT"),
    @OptionItem(label = "Japanese / 日本語")
    JA_JP("ja-JP"),
    @OptionItem(label = "Georgian / ქართული ენა")
    KA_GE("ka-GE"),
    @OptionItem(label = "Kabyle / Taqbaylit")
    KAB_DZ("kab-DZ"),
    @OptionItem(label = "Khmer / ភាសាខ្មែរ")
    KM_KH("km-KH"),
    @OptionItem(label = "Korean (KP) / 한국어 (KP)")
    KO_KP("ko-KP"),
    @OptionItem(label = "Korean (KR) / 한국어 (KR)")
    KO_KR("ko-KR"),
    @OptionItem(label = "Luxembourgish")
    LB_LU("lb-LU"),
    @OptionItem(label = "Lao / ພາສາລາວ")
    LO_LA("lo-LA"),
    @OptionItem(label = "Lithuanian / Lietuvių kalba")
    LT_LT("lt-LT"),
    @OptionItem(label = "Latvian / latviešu valoda")
    LV_LV("lv-LV"),
    @OptionItem(label = "Macedonian / македонски јазик")
    MK_MK("mk-MK"),
    @OptionItem(label = "Malayalam / മലയാളം")
    ML_IN("ml-IN"),
    @OptionItem(label = "Mongolian / монгол")
    MN_MN("mn-MN"),
    @OptionItem(label = "Malay / Bahasa Melayu")
    MS_MY("ms-MY"),
    @OptionItem(label = "Burmese / ဗမာစာ")
    MY_MM("my-MM"),
    @OptionItem(label = "Norwegian Bokmål / Norsk bokmål")
    NB_NO("nb-NO"),
    @OptionItem(label = "Dutch (BE) / Nederlands (BE)")
    NL_BE("nl-BE"),
    @OptionItem(label = "Dutch / Nederlands")
    NL_NL("nl-NL"),
    @OptionItem(label = "Polish / Język polski")
    PL_PL("pl-PL"),
    @OptionItem(label = "Portuguese (AO) / Português (AO)")
    PT_AO("pt-AO"),
    @OptionItem(label = "Portuguese (BR) / Português (BR)")
    PT_BR("pt-BR"),
    @OptionItem(label = "Portuguese / Português")
    PT_PT("pt-PT"),
    @OptionItem(label = "Romanian / română")
    RO_RO("ro-RO"),
    @OptionItem(label = "Russian / русский язык")
    RU_RU("ru-RU"),
    @OptionItem(label = "Slovak / Slovenský jazyk")
    SK_SK("sk-SK"),
    @OptionItem(label = "Slovenian / slovenščina")
    SL_SI("sl-SI"),
    @OptionItem(label = "Albanian / Shqip")
    SQ_AL("sq-AL"),
    @OptionItem(label = "Serbian (Latin) / srpski")
    SR_LATIN("sr-latin"),
    @OptionItem(label = "Serbian (Cyrillic) / српски")
    SR_RS("sr-RS"),
    @OptionItem(label = "Swedish / Svenska")
    SV_SE("sv-SE"),
    @OptionItem(label = "Telugu / తెలుగు")
    TE_IN("te-IN"),
    @OptionItem(label = "Thai / ภาษาไทย")
    TH_TH("th-TH"),
    @OptionItem(label = "Tagalog / Filipino")
    TL_PH("tl-PH"),
    @OptionItem(label = "Turkish / Türkçe")
    TR_TR("tr-TR"),
    @OptionItem(label = "Ukrainian / українська")
    UK_UA("uk-UA"),
    @OptionItem(label = "Vietnamese / Tiếng Việt")
    VI_VN("vi-VN"),
    @OptionItem(label = "Chinese (Simplified) / 简体中文")
    ZH_CN("zh-CN"),
    @OptionItem(label = "Chinese (HK)")
    ZH_HK("zh-HK"),
    @OptionItem(label = "Chinese (Traditional) / 繁體中文")
    ZH_TW("zh-TW"),;

    @JsonValue
    private final String code;

    /**
     * code map
     */
    private static final Map<String, Language> codeMap = Stream.of(values())
            .collect(Collectors.toMap(Language::getCode, Function.identity()));

    /**
     * Locale per member, precomputed from the BCP-47 code (same pattern as codeMap).
     */
    private static final Map<Language, Locale> localeMap = Stream.of(values())
            .collect(Collectors.toMap(Function.identity(), l -> Locale.forLanguageTag(l.code)));

    /**
     * Get language item by languageCode, compatible with the format of `_` separator.
     *
     * @param languageCode language code
     * @return Language
     */
    public static Language of(String languageCode) {
        Assert.notBlank(languageCode, "languageCode cannot be empty!");
        Language language = codeMap.get(languageCode);
        if (language == null) {
            languageCode = languageCode.replace("_", "-");
            language = codeMap.get(languageCode);
        }
        if (language == null) {
            log.error("Language code {} is not supported, please check your format.", languageCode);
        }
        return language;
    }

    /**
     * Check if the language exists
     *
     * @param languageCode language code
     * @return boolean
     */
    public static boolean exists(String languageCode) {
        boolean exist = codeMap.containsKey(languageCode);
        if (!exist) {
            languageCode = languageCode.replace("_", "-");
            exist = codeMap.containsKey(languageCode);
        }
        return exist;
    }

    // ------------------------------------------------------------------
    // Locale-derived formatting facts (CLDR via the JDK).
    //
    // These are fixed facts of the locale — never stored, never
    // tenant-overridable. The JDK ships the authoritative CLDR dataset
    // (default locale provider since Java 9); browsers expose the same data
    // through Intl.*, so the frontend derives identically from the tag alone.
    // Platform conventions: MEDIUM date style, SHORT time style.
    // ------------------------------------------------------------------

    /**
     * The {@link Locale} for this language's BCP-47 tag.
     */
    public Locale toLocale() {
        return localeMap.get(this);
    }

    /**
     * CLDR decimal separator for this locale (e.g. {@code ','} for pt-BR).
     */
    public char decimalSeparator() {
        return DecimalFormatSymbols.getInstance(toLocale()).getDecimalSeparator();
    }

    /**
     * CLDR grouping (thousand) separator for this locale
     * (e.g. {@code '.'} for pt-BR, {@code '’'} for de-CH).
     */
    public char groupingSeparator() {
        return DecimalFormatSymbols.getInstance(toLocale()).getGroupingSeparator();
    }

    /**
     * CLDR localized date pattern (platform convention: {@link FormatStyle#MEDIUM}),
     * e.g. {@code "d 'de' MMM 'de' y"} for pt-BR, {@code "y年M月d日"} for zh-CN.
     */
    public String datePattern() {
        return DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.MEDIUM, null, IsoChronology.INSTANCE, toLocale());
    }

    /**
     * CLDR localized time pattern (platform convention: {@link FormatStyle#SHORT}),
     * e.g. {@code "HH:mm"} for zh-CN, {@code "h:mm a"} for en-US.
     */
    public String timePattern() {
        return DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                null, FormatStyle.SHORT, IsoChronology.INSTANCE, toLocale());
    }

}
