package io.softa.framework.base.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class StringToolsTest {

    @Test
    void toUnderscoreCase() {
        Assertions.assertEquals("http_server", StringTools.toUnderscoreCase("HTTPServer"));
        Assertions.assertEquals("json", StringTools.toUnderscoreCase("JSON"));
        Assertions.assertEquals("json", StringTools.toUnderscoreCase("Json"));
        Assertions.assertEquals("s9001_test", StringTools.toUnderscoreCase("S9001Test"));
    }

    @Test
    void humanize() {
        // CamelCase
        Assertions.assertEquals("Dept Info", StringTools.humanize("DeptInfo"));
        Assertions.assertEquals("Dept Id", StringTools.humanize("deptId"));
        Assertions.assertEquals("Tenant Status", StringTools.humanize("TenantStatus"));
        // UPPER_SNAKE
        Assertions.assertEquals("Multi File", StringTools.humanize("MULTI_FILE"));
        Assertions.assertEquals("Db Auto Id", StringTools.humanize("DB_AUTO_ID"));
        // single word
        Assertions.assertEquals("Code", StringTools.humanize("code"));
        Assertions.assertEquals("Name", StringTools.humanize("name"));
        // acronym boundary
        Assertions.assertEquals("Http Server", StringTools.humanize("HTTPServer"));
        // blank passthrough
        Assertions.assertEquals("", StringTools.humanize(""));
        Assertions.assertNull(StringTools.humanize(null));
    }

    @Test
    void joinDisplayNameNamesARowByEveryDisplayField() {
        // A model named by {name, code}: the pair is what tells two rows sharing a name apart.
        Assertions.assertEquals("Project Manager / PM",
                StringTools.joinDisplayName(java.util.List.of("Project Manager", "PM")));
    }

    @Test
    void joinDisplayNameSaysARepeatedValueOnce() {
        // A row whose code was filled in with its name. "Branch / Branch" carries the same word twice
        // and nothing more; the row still reads as itself.
        Assertions.assertEquals("Branch", StringTools.joinDisplayName(java.util.List.of("Branch", "Branch")));
        Assertions.assertEquals("Branch", StringTools.joinDisplayName(java.util.List.of("Branch", " Branch ")));
        // Only the repeat goes; a value that differs, even by a suffix, is exactly what distinguishes.
        Assertions.assertEquals("Branch / Branch-02",
                StringTools.joinDisplayName(java.util.List.of("Branch", "Branch-02")));
    }

    @Test
    void joinDisplayNameSkipsWhatIsNotThere() {
        java.util.List<Object> withGaps = new java.util.ArrayList<>();
        withGaps.add("Wei Zhang");
        withGaps.add(null);
        withGaps.add("");
        withGaps.add("E1001");
        Assertions.assertEquals("Wei Zhang / E1001", StringTools.joinDisplayName(withGaps));
        Assertions.assertEquals("", StringTools.joinDisplayName(java.util.List.of()));
        Assertions.assertEquals("", StringTools.joinDisplayName(null));
    }

    @Test
    void joinDisplayNameKeepsNonStringValues() {
        // Display fields are not always text: an option label or a number reads as its string form.
        Assertions.assertEquals("Level / 3", StringTools.joinDisplayName(java.util.List.of("Level", 3)));
    }
}
