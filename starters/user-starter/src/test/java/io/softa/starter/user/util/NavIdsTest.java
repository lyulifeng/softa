package io.softa.starter.user.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NavIdsTest {

    @Test
    void moduleOf_handlesPrefixedAndBare() {
        assertThat(NavIds.moduleOf("navigation.core-hr.emp.list")).isEqualTo("core-hr");
        assertThat(NavIds.moduleOf("core-hr.emp")).isEqualTo("core-hr");
        assertThat(NavIds.moduleOf("navigation.ai")).isEqualTo("ai");
        assertThat(NavIds.moduleOf(null)).isNull();
        assertThat(NavIds.moduleOf("  ")).isNull();
    }
}
