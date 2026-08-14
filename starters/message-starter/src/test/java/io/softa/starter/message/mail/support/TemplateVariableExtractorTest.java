package io.softa.starter.message.mail.support;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.softa.starter.message.mail.dto.MailTemplateVariableDTO;
import io.softa.starter.message.mail.dto.TemplateVariableKind;
import io.softa.starter.message.mail.entity.MailTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateVariableExtractorTest {

    private static MailTemplate template(String subject, String html, String text) {
        MailTemplate t = new MailTemplate();
        t.setSubject(subject);
        t.setBodyHtml(html);
        t.setBodyText(text);
        return t;
    }

    private static List<String> names(List<MailTemplateVariableDTO> vars) {
        return vars.stream().map(MailTemplateVariableDTO::getName).toList();
    }

    @Test
    void dedupesAcrossColumns_inFirstAppearanceOrder() {
        List<MailTemplateVariableDTO> vars = TemplateVariableExtractor.extract(template(
                "Welcome, {{ name }}!",
                "<p>Hi {{ name }}, click {{ activationUrl }}</p>",
                "Hi {{ name }}: {{ activationUrl }} / {{ supportEmail }}"));

        assertEquals(List.of("name", "activationUrl", "supportEmail"), names(vars));
        assertTrue(vars.stream().allMatch(v -> v.getKind() == TemplateVariableKind.VARIABLE));
    }

    @Test
    void classifiesDottedPathsAsVariables_andExpressionsAsExpressions() {
        List<MailTemplateVariableDTO> vars = TemplateVariableExtractor.extract(template(
                "{{ user.name }}", "<p>{{ a + b }}</p>", null));

        assertEquals(TemplateVariableKind.VARIABLE, vars.get(0).getKind());
        assertEquals("user.name", vars.get(0).getName());
        assertEquals(TemplateVariableKind.EXPRESSION, vars.get(1).getKind());
    }

    @Test
    void unicodeSimpleNames_areInputableVariables() {
        List<MailTemplateVariableDTO> vars = TemplateVariableExtractor.extract(template(
                "{{ 签名人 }}", "<p>{{ 文件名称的变量 }} vs {{ 数量 + 1 }}</p>", null));

        assertEquals(TemplateVariableKind.VARIABLE, vars.get(0).getKind());
        assertEquals("签名人", vars.get(0).getName());
        assertEquals(TemplateVariableKind.VARIABLE, vars.get(1).getKind());
        assertEquals(TemplateVariableKind.EXPRESSION, vars.get(2).getKind());
    }

    @Test
    void forLoop_surfacesCollection_andExcludesLoopLocals() {
        List<MailTemplateVariableDTO> vars = TemplateVariableExtractor.extract(template(
                null,
                "<ul>{% for item in items %}<li>{{ item.name }} #{{ loop.index }}</li>{% endfor %}</ul>"
                        + "<p>{{ footerNote }}</p>",
                null));

        // item.name and loop.index are template-local — no bogus inputs.
        assertEquals(List.of("items", "footerNote"), names(vars));
        assertEquals(TemplateVariableKind.COLLECTION, vars.get(0).getKind());
        assertEquals(TemplateVariableKind.VARIABLE, vars.get(1).getKind());
    }

    @Test
    void collectionUpgradesEarlierVariableSighting_ofSameName() {
        List<MailTemplateVariableDTO> vars = TemplateVariableExtractor.extract(template(
                null,
                "<p>{{ items }}</p>{% for i in items %}{{ i }}{% endfor %}",
                null));

        assertEquals(List.of("items"), names(vars));
        assertEquals(TemplateVariableKind.COLLECTION, vars.get(0).getKind());
    }

    @Test
    void setTargets_areLocals_andComplexForIterableIsExpression() {
        List<MailTemplateVariableDTO> vars = TemplateVariableExtractor.extract(template(
                null,
                "{% set greeting = 'Hi' %}{{ greeting }}"
                        + "{% for u in users | slice(0, 3) %}{{ u.mail }}{% endfor %}",
                null));

        assertEquals(List.of("users | slice(0, 3)"), names(vars));
        assertEquals(TemplateVariableKind.EXPRESSION, vars.get(0).getKind());
    }

    @Test
    void bareSimpleNameIfCondition_surfacesAsVariable_complexIgnored() {
        List<MailTemplateVariableDTO> vars = TemplateVariableExtractor.extract(template(
                null,
                "{% if isVip %}VIP{% endif %}{% if count > 3 %}many{% endif %}",
                null));

        assertEquals(List.of("isVip"), names(vars));
        assertEquals(TemplateVariableKind.VARIABLE, vars.get(0).getKind());
    }

    @Test
    void subjectIsNotPebble_tagsThereAreNotScanned() {
        List<MailTemplateVariableDTO> vars = TemplateVariableExtractor.extract(template(
                "{% for x in rows %} is literal here, {{ title }} is not",
                null, null));

        assertEquals(List.of("title"), names(vars));
    }

    @Test
    void emptyTemplate_yieldsEmptyList() {
        assertTrue(TemplateVariableExtractor.extract(template(null, null, null)).isEmpty());
        assertTrue(TemplateVariableExtractor.extract(template("no placeholders", "<p>x</p>", "y")).isEmpty());
    }
}
