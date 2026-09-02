package io.softa.starter.file.service.impl;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.softa.starter.file.entity.ExportHistory;
import io.softa.starter.file.entity.ImportHistory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The file an import or export produces is stamped with the model that HOLDS it.
 *
 * <p>This is here because the mechanism tests and the pipeline tests each proved their own half and
 * the bug lived in the seam between them. {@code FileClaimableTest} showed the ownership rule works;
 * the import tests all enter partway down the pipeline with rows already prepared, so none of them
 * ever uploads a file or writes an ImportHistory. Between the two, every import in the product was
 * failing with "File … is not yours to attach" and nothing went red.
 *
 * <p>Asserted against the source rather than by running an import, deliberately: the call sites are
 * what regressed, they are one line each, and reaching them for real needs OSS, a database and a
 * template. A test that cannot run is worth less than a blunt one that does.
 */
class ImportFileIsStampedWithItsHolderTest {

    private static final String IMPORT_SERVICE =
            "starters/file-starter/src/main/java/io/softa/starter/file/service/impl/ImportServiceImpl.java";
    private static final List<String> EXPORT_STRATEGIES = List.of(
            "starters/file-starter/src/main/java/io/softa/starter/file/excel/export/strategy/ExportByDynamic.java",
            "starters/file-starter/src/main/java/io/softa/starter/file/excel/export/strategy/ExportByFieldTemplate.java");

    @Test
    @DisplayName("no import upload is stamped with the model being imported")
    void importsStampTheHistoryNotTheSubject() {
        String source = SourceFiles.read(IMPORT_SERVICE);

        assertThat(source)
                .as("the file hangs on ImportHistory, so that is the model that may claim it — "
                        + "stamping the imported model has the history write refused")
                .doesNotContain("uploadFile(importTemplate.getModelName()")
                .doesNotContain("uploadFile(importWizard.getModelName()")
                .doesNotContain("generateFileAndUpload(importTemplateDTO.getModelName()");
        assertThat(SourceFiles.countOf(source, "ImportHistory.class.getSimpleName()"))
                .as("one constant, used by every upload in this class")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("no export upload is stamped with the model being exported, or left blank")
    void exportsStampTheHistoryNotTheSubject() {
        for (String path : EXPORT_STRATEGIES) {
            String source = SourceFiles.read(path);
            assertThat(source)
                    .as("%s stamps the exported model", path)
                    .doesNotContain("generateFileAndUpload(modelName,")
                    .doesNotContain("generateFileAndUpload(exportTemplate.getModelName()");
            // A blank model is not a safe default here: isClaimableBy treats an unclaimed record with
            // no model as claimable by ANY model, which is looser than the multi-sheet export needs.
            assertThat(source)
                    .as("%s leaves a file unstamped", path)
                    .doesNotContain("generateFileAndUpload(\"\",");
            assertThat(source).contains(ExportHistory.class.getSimpleName() + ".class.getSimpleName()");
        }
    }

    @Test
    @DisplayName("the two holder models are the ones the File fields actually live on")
    void theHoldersAreWhereTheFileFieldsAre() {
        // Guards the constants themselves: rename either entity and the stamp has to follow, or the
        // product breaks again in exactly the way this test exists to prevent.
        assertThat(ImportHistory.class.getSimpleName()).isEqualTo("ImportHistory");
        assertThat(ExportHistory.class.getSimpleName()).isEqualTo("ExportHistory");
    }
}
