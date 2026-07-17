package io.softa.framework.base.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * {@link JsonUtils#toStringList(Object)} handles every shape a JSON-array value can
 * take when a JSON column is read via the ModelService Map overload (约定读): a JSON
 * string, an already-parsed {@link JsonNode}, or a {@link java.util.Collection}.
 * Strict-string, null-on-empty, never throws.
 */
class JsonUtilsToStringListTest {

    @Test
    void nullInput_returnsNull() {
        assertThat(JsonUtils.toStringList(null)).isNull();
    }

    @Test
    void jsonStringArray_isParsed() {
        assertThat(JsonUtils.toStringList("[\"/a\",\"/b\"]")).containsExactly("/a", "/b");
    }

    @Test
    void jsonNodeArray_isRead() {
        JsonNode node = JsonUtils.stringToObject("[\"x\",\"y\"]", JsonNode.class);
        assertThat(JsonUtils.toStringList(node)).containsExactly("x", "y");
    }

    @Test
    void collection_isStringified() {
        assertThat(JsonUtils.toStringList(List.of("m", "n"))).containsExactly("m", "n");
    }

    @Test
    void blankString_returnsNull() {
        assertThat(JsonUtils.toStringList("")).isNull();
        assertThat(JsonUtils.toStringList("   ")).isNull();
    }

    @Test
    void emptyArray_returnsNull() {
        assertThat(JsonUtils.toStringList("[]")).isNull();
    }

    @Test
    void nonArrayJson_returnsNull() {
        assertThat(JsonUtils.toStringList("\"scalar\"")).isNull();
        assertThat(JsonUtils.toStringList("{\"k\":1}")).isNull();
    }

    @Test
    void numericElements_droppedStrictString() {
        assertThat(JsonUtils.toStringList("[1,2,3]")).isNull();
        assertThat(JsonUtils.toStringList("[\"a\",1,\"b\"]")).containsExactly("a", "b");
    }

    @Test
    void numericElements_keptWhenCoerceNumeric() {
        assertThat(JsonUtils.toStringList("[1,2,3]", true)).containsExactly("1", "2", "3");
        assertThat(JsonUtils.toStringList("[\"a\",1]", true)).containsExactly("a", "1");
    }

    @Test
    void malformedJson_returnsNull_neverThrows() {
        assertThat(JsonUtils.toStringList("[not json")).isNull();
    }

    @Test
    void unsupportedType_returnsNull() {
        assertThat(JsonUtils.toStringList(Integer.valueOf(5))).isNull();
    }
}
