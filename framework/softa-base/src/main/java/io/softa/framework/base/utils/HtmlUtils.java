package io.softa.framework.base.utils;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

/**
 * HTML utility class — thin wrappers around Jsoup for common conversions.
 */
public class HtmlUtils {

    /**
     * Strip HTML markup to plain text. Returns {@code null} for null or blank
     * input so callers can use it transparently as a fallback.
     */
    public static String toText(String html) {
        if (StringUtils.isBlank(html)) return null;
        return Jsoup.parse(html).text();
    }
}
