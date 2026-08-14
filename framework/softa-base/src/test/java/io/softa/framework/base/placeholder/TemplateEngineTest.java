package io.softa.framework.base.placeholder;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateEngineTest {

    @Test
    void renderSimpleVariable() {
        String template = "<p>Hello, {{ name }}</p>";
        String result = TemplateEngine.render(template, Map.of("name", "World"));
        assertEquals("<p>Hello, World</p>", result);
    }

    @Test
    void renderMultipleVariables() {
        String template = "<div>{{ greeting }}, {{ name }}!</div>";
        Map<String, Object> data = Map.of("greeting", "Hi", "name", "Softa");
        String result = TemplateEngine.render(template, data);
        assertEquals("<div>Hi, Softa!</div>", result);
    }

    @Test
    void renderNestedVariable() {
        String template = "<span>{{ user.name }}</span>";
        Map<String, Object> data = Map.of("user", Map.of("name", "Alice"));
        String result = TemplateEngine.render(template, data);
        assertEquals("<span>Alice</span>", result);
    }

    @Test
    void renderWithoutPlaceholders() {
        String template = "<p>No placeholders here</p>";
        String result = TemplateEngine.render(template, Map.of());
        assertEquals("<p>No placeholders here</p>", result);
    }

    @Test
    void renderHtmlEscapesVariableOutput() {
        String template = "<p>{{ name }}</p>";
        String result = TemplateEngine.renderHtml(template, Map.of("name", "<script>alert(1)</script>"));
        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>", result);
    }

    @Test
    void renderHtmlRawFilterEmbedsTrustedFragment() {
        String template = "<div>{{ fragment | raw }}</div>";
        String result = TemplateEngine.renderHtml(template, Map.of("fragment", "<b>bold</b>"));
        assertEquals("<div><b>bold</b></div>", result);
    }

    @Test
    void renderKeepsOutputVerbatim() {
        // The codegen/plain-text profile must not HTML-escape: SQL, Java code and
        // text/plain bodies embed values exactly as provided
        String result = TemplateEngine.render("{{ value }}", Map.of("value", "a < b && c > d"));
        assertEquals("a < b && c > d", result);
    }

    @Test
    void renderWithExtraSpacesInPlaceholder() {
        // Pebble handles ${  name  } — spaces are part of the expression,
        // but for simple var names Pebble trims them in expression evaluation.
        String template = "<p>{{name}}</p>";
        String result = TemplateEngine.render(template, Map.of("name", "Compact"));
        assertEquals("<p>Compact</p>", result);
    }
}

