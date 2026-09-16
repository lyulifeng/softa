package io.softa.framework.orm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * A boolean right-hand side works in the JSON form and not in the expression form.
 *
 * <p>The grammar declares {@code BOOLEAN: 'true' | 'false'} and the visitor handles it, but the
 * rule sits below {@code FIELD: [a-z][a-zA-Z0-9]*} in the lexer. Both match {@code true} at the
 * same length, ANTLR breaks that tie by declaration order, so the literal lexes as a field name and
 * the unit never reaches a value. Moving BOOLEAN above FIELD fixes it, but the generated lexer is
 * committed to the repository and there is no build plugin to regenerate it, so the fix needs the
 * ANTLR tool run by hand. Until then this test records what actually works, so nobody writes a
 * declaration against the grammar file and finds out at boot.
 */
class BooleanLiteralExpressionTest {

    @Test
    void theJsonFormAcceptsABooleanRightHandSide() {
        assertThat(Filters.of("[[\"hasProbation\", \"!=\", true]]")).isNotNull();
        assertThat(Filters.of("[[\"policyEnableEntitlement\", \"=\", false]]")).isNotNull();
    }

    @Test
    void theExpressionFormDoesNotYet() {
        assertThatThrownBy(() -> Filters.of("hasProbation != true"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported value context");
    }
}
