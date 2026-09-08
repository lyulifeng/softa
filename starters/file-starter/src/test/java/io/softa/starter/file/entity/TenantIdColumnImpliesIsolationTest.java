package io.softa.starter.file.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * A {@code tenantId} column on a model that is not {@code multiTenant} is not a smaller version of
 * isolation — it is none of it, silently.
 *
 * <p>Both halves of the mechanism read the flag, not the column:
 * {@code AutofillFields.fillTenantFieldForInsert} stamps the tenant only when
 * {@code isMultiTenantControl(modelName)}, and {@code WhereBuilder} narrows the read only for the
 * same models. So a model with the column and without the flag writes every row with a null tenant
 * and serves every row to every tenant, while looking — in the entity, in the table, in a
 * {@code SELECT} — exactly like a model that is isolated.
 *
 * <p>That is what {@link DocumentTemplate} did. It carried the column from the start, every model
 * around it in this package was isolated, its own child {@link DocumentTemplateSignSlot} was
 * isolated, and it was not. Nothing failed, nothing warned, and every tenant could read every other
 * tenant's contract wording.
 *
 * <p>Read from source rather than through reflection on purpose: the check is about what the
 * declaration says, and a source scan also catches an entity added tomorrow without anyone
 * remembering this rule.
 */
class TenantIdColumnImpliesIsolationTest {

    private static final Path ENTITY_DIR =
            Path.of("src/main/java/io/softa/starter/file/entity");

    /** The annotation on its own line — never one named inside a javadoc or an import. */
    private static final Pattern MODEL_ANNOTATION =
            Pattern.compile("^[ \\t]*@Model\\b", Pattern.MULTILINE);

    private static final Pattern TENANT_ID_FIELD =
            Pattern.compile("private\\s+Long\\s+tenantId\\s*;");

    @Test
    void everyEntityCarryingTenantIdDeclaresMultiTenant() throws IOException {
        List<String> offenders = new ArrayList<>();
        int checked = 0;

        for (Path file : entityFiles()) {
            String source = Files.readString(file);
            if (!TENANT_ID_FIELD.matcher(source).find()) {
                continue;
            }
            checked++;
            if (!declaresMultiTenant(source, file)) {
                offenders.add(file.getFileName().toString());
            }
        }

        assertThat(checked).as("scanned no entity with a tenantId field — did the package move?")
                .isGreaterThan(5);
        assertThat(offenders)
                .as("These carry a tenantId column but no `multiTenant = true`, so the column does "
                        + "nothing: rows are written with a null tenant and read by every tenant")
                .isEmpty();
    }

    @Test
    void documentTemplateIsIsolatedAlongsideItsSignSlots() throws IOException {
        // Named outright, not left to the sweep above: a parent shared while its children are
        // per-tenant is the specific shape that was wrong here, and a sweep says nothing about
        // whether the pair still agrees.
        assertThat(declaresMultiTenant(read("DocumentTemplate.java"), null))
                .as("DocumentTemplate holds the document body — the part with names and salaries in it")
                .isTrue();
        assertThat(declaresMultiTenant(read("DocumentTemplateSignSlot.java"), null)).isTrue();
    }

    private static boolean declaresMultiTenant(String source, Path file) {
        Matcher matcher = MODEL_ANNOTATION.matcher(source);
        if (!matcher.find()) {
            return fail("no @Model on the class in " + (file == null ? "the source" : file));
        }
        return modelArguments(source, matcher.end()).contains("multiTenant = true");
    }

    /**
     * The annotation's argument list, or empty for a bare {@code @Model}.
     *
     * <p>Brackets are balanced rather than matched with a regex: an argument can itself carry
     * braces ({@code businessKey = {"code"}}), and a `@Model` is often followed by other
     * annotations before the class line, so neither "up to the first `)`" nor "up to `public
     * class`" reads the right span.
     */
    private static String modelArguments(String source, int afterAnnotationName) {
        int at = afterAnnotationName;
        while (at < source.length() && Character.isWhitespace(source.charAt(at))) {
            at++;
        }
        if (at >= source.length() || source.charAt(at) != '(') {
            return "";
        }
        int depth = 0;
        for (int end = at; end < source.length(); end++) {
            char ch = source.charAt(end);
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && --depth == 0) {
                return source.substring(at + 1, end);
            }
        }
        return fail("unbalanced @Model(...) in " + (file(source)));
    }

    private static String file(String source) {
        int at = source.indexOf("public class");
        return at < 0 ? "the source" : source.substring(at, Math.min(source.length(), at + 60));
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(locate().resolve(fileName));
    }

    private static List<Path> entityFiles() throws IOException {
        try (Stream<Path> files = Files.list(locate())) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
        }
    }

    /** The entity directory, from wherever the test was launched (module dir or repo root). */
    private static Path locate() {
        Path here = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4 && here != null; i++, here = here.getParent()) {
            Path candidate = here.resolve(ENTITY_DIR);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            Path fromRoot = here.resolve("starters/file-starter").resolve(ENTITY_DIR);
            if (Files.isDirectory(fromRoot)) {
                return fromRoot;
            }
        }
        return fail("could not find " + ENTITY_DIR + " from " + Path.of("").toAbsolutePath());
    }
}
