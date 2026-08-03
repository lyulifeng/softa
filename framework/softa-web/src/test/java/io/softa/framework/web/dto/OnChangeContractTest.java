package io.softa.framework.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * The wire shape of the /onChange/{fieldName} API. The request carries the id of the row being
 * edited, the new value of the changed field and the current values of the fields it declared in
 * {@code with}; the response carries the values to write back plus one complete field-name list per
 * rule state, so a field left out of a list is reset rather than left as it was.
 */
class OnChangeContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestBindsIdValueAndCompanionValues() throws Exception {
        OnChangeParams params = objectMapper.readValue("{\"id\":\"700001\",\"value\":\"600001\",\"values\":{\"overtimeDate\":\"2026-01-05\"}}",OnChangeParams.class);

        Assertions.assertEquals("700001", params.getId());
        Assertions.assertEquals("600001", params.getValue());
        Assertions.assertEquals(Map.of("overtimeDate", "2026-01-05"), params.getValues());
    }

    /** Editing a row that does not exist yet, from a field that declared no {@code with}. */
    @Test
    void requestOfANewRowCarriesTheChangedValueAlone() throws Exception {
        OnChangeParams params = objectMapper.readValue("{\"value\":\"600001\"}", OnChangeParams.class);

        Assertions.assertEquals("600001", params.getValue());
        Assertions.assertNull(params.getId());
        Assertions.assertNull(params.getValues());
    }

    /** {@code value} is an Object, not a String: the changed field can be a boolean or a number too. */
    @Test
    void requestValueTakesAnyJsonScalar() throws Exception {
        Assertions.assertEquals(Boolean.TRUE, objectMapper.readValue("{\"value\":true}", OnChangeParams.class).getValue());
        Assertions.assertEquals(Integer.valueOf(42), objectMapper.readValue("{\"value\":42}", OnChangeParams.class).getValue());
        Assertions.assertEquals("2026-01-05", objectMapper.readValue("{\"value\":\"2026-01-05\"}", OnChangeParams.class).getValue());
    }

    /** Each rule state is one array of field names; the states the endpoint says nothing about stay null. */
    @Test
    void responseSerializesEachRuleStateAsAFieldNameList() throws Exception {
        String json = objectMapper.writeValueAsString(OnChangeResponse.builder()
                .values(Map.of("payDate", "2026-03-07"))
                .readonly(List.of("periodStart", "payDate"))
                .build());

        JsonNode response = objectMapper.readTree(json);
        Assertions.assertEquals("2026-03-07", response.get("values").get("payDate").asText());
        Assertions.assertTrue(response.get("readonly").isArray());
        Assertions.assertEquals("periodStart", response.get("readonly").get(0).asText());
        Assertions.assertEquals("payDate", response.get("readonly").get(1).asText());
        Assertions.assertTrue(response.get("required").isNull());
        Assertions.assertTrue(response.get("hidden").isNull());
    }
}
