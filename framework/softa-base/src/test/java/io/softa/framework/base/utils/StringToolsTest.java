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
}
