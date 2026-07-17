package io.softa.starter.permission.index;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import io.softa.starter.permission.interceptor.PermissionInterceptorProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the engine-side rule ① coverage check (extracted from
 * user-starter's {@code PermissionRegistryValidator} in the 2026-07-17 ⊥
 * decouple). Exercises the package-private {@code findUncoveredEndpoints} seam
 * so the pure logic is asserted without a live application context.
 */
class EndpointCoverageValidatorTest {

    private EndpointIndex endpointIndex;
    private PermissionInterceptorProperties props;

    /** RequestMappingInfo built without a PatternParser exposes a null
     *  {@code getPathPatternsCondition()}; configure one so patterns resolve
     *  the same way they do at runtime (Spring MVC installs a PathPatternParser). */
    private static final RequestMappingInfo.BuilderConfiguration PATTERN_CFG =
            new RequestMappingInfo.BuilderConfiguration();
    static {
        PATTERN_CFG.setPatternParser(new PathPatternParser());
    }

    @BeforeEach
    void setUp() {
        endpointIndex = mock(EndpointIndex.class);
        props = new PermissionInterceptorProperties();
    }

    // ── helpers ──

    private static RequestMappingInfo info(String path, RequestMethod... methods) {
        return RequestMappingInfo.paths(path).methods(methods).options(PATTERN_CFG).build();
    }

    /** The {@code HandlerMethod} value is never dereferenced by the validator
     *  (only the {@code RequestMappingInfo} key is), so a bare mock suffices. */
    private static HandlerMethod handler() {
        return mock(HandlerMethod.class);
    }

    private static Map<RequestMappingInfo, HandlerMethod> handlers(RequestMappingInfo... infos) {
        Map<RequestMappingInfo, HandlerMethod> m = new HashMap<>();
        for (RequestMappingInfo i : infos) m.put(i, handler());
        return m;
    }

    private EndpointCoverageValidator validator() {
        // handlerMappings is irrelevant when we call findUncoveredEndpoints directly.
        return new EndpointCoverageValidator(endpointIndex, props, List.of());
    }

    // ── tests ──

    @Test
    void coveredEndpoint_notReported() {
        when(endpointIndex.lookup("/Employee/searchList", "POST")).thenReturn(Set.of("employee.view"));
        Set<String> uncovered = validator().findUncoveredEndpoints(
                handlers(info("/Employee/searchList", RequestMethod.POST)));
        assertThat(uncovered).isEmpty();
    }

    @Test
    void uncoveredEndpoint_reported() {
        when(endpointIndex.lookup(anyString(), anyString())).thenReturn(Set.of());
        Set<String> uncovered = validator().findUncoveredEndpoints(
                handlers(info("/Employee/searchList", RequestMethod.POST)));
        assertThat(uncovered).containsExactly("POST /Employee/searchList");
    }

    @Test
    void nullLookupResult_treatedAsUncovered() {
        when(endpointIndex.lookup(anyString(), anyString())).thenReturn(null);
        Set<String> uncovered = validator().findUncoveredEndpoints(
                handlers(info("/Employee/searchList", RequestMethod.POST)));
        assertThat(uncovered).containsExactly("POST /Employee/searchList");
    }

    @Test
    void publicPattern_skipsEndpoint() {
        props.setPublicUriPatterns(List.of("/auth/**"));
        when(endpointIndex.lookup(anyString(), anyString())).thenReturn(Set.of());
        Set<String> uncovered = validator().findUncoveredEndpoints(
                handlers(info("/auth/login", RequestMethod.POST)));
        assertThat(uncovered).isEmpty();
    }

    @Test
    void authenticatedBypassPattern_skipsEndpoint() {
        props.setAuthenticatedBypassPatterns(List.of("/me/**"));
        when(endpointIndex.lookup(anyString(), anyString())).thenReturn(Set.of());
        Set<String> uncovered = validator().findUncoveredEndpoints(
                handlers(info("/me/uiContext", RequestMethod.GET)));
        assertThat(uncovered).isEmpty();
    }

    @Test
    void frameworkInfraPath_skipped() {
        when(endpointIndex.lookup(anyString(), anyString())).thenReturn(Set.of());
        Set<String> uncovered = validator().findUncoveredEndpoints(handlers(
                info("/actuator/health", RequestMethod.GET),
                info("/error", RequestMethod.GET),
                info("/swagger-ui/index.html", RequestMethod.GET)));
        assertThat(uncovered).isEmpty();
    }

    @Test
    void noMethodCondition_probesAllVerbs() {
        // A mapping with no explicit method condition serves every verb, so an
        // uncovered route must be reported once per probed verb (GET/POST/PUT/PATCH/DELETE).
        when(endpointIndex.lookup(anyString(), anyString())).thenReturn(Set.of());
        Set<String> uncovered = validator().findUncoveredEndpoints(
                handlers(info("/Legacy/open")));   // no methods → all verbs
        assertThat(uncovered).containsExactlyInAnyOrder(
                "GET /Legacy/open", "POST /Legacy/open", "PUT /Legacy/open",
                "PATCH /Legacy/open", "DELETE /Legacy/open");
    }

    @Test
    void mixedCoverage_reportsOnlyUncovered() {
        when(endpointIndex.lookup("/Employee/searchList", "POST")).thenReturn(Set.of("employee.view"));
        when(endpointIndex.lookup("/Salary/searchList", "POST")).thenReturn(Set.of());
        Set<String> uncovered = validator().findUncoveredEndpoints(handlers(
                info("/Employee/searchList", RequestMethod.POST),
                info("/Salary/searchList", RequestMethod.POST)));
        assertThat(uncovered).containsExactly("POST /Salary/searchList");
    }
}
