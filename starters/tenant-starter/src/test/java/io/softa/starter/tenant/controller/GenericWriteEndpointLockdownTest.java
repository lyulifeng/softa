package io.softa.starter.tenant.controller;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that both subscription controllers shadow <b>every</b> generic write endpoint.
 *
 * <p>The framework's generic controller is mapped at {@code /{modelName}}, so a model gains sixteen write
 * endpoints just by existing, and {@code ModelServiceImpl} does not route through the model's
 * {@code EntityService} — overriding the service does not close them. Each unshadowed path can insert an
 * overlapping period (making "the period covering today" ambiguous, so the projection picks one
 * arbitrarily and the wrong plan is granted) or skip the projection refresh.
 *
 * <p>Reviewing sixteen names by hand is exactly the kind of thing that gets missed once and stays missed,
 * and this codebase has been bitten twice already — {@code TenantStatus} editable through generic
 * {@code updateOne}, and the {@code user_info} cache bypassed by {@code updateByFilter}. So the list is
 * asserted mechanically: adding a write endpoint upstream fails this test until it is dealt with here.
 */
class GenericWriteEndpointLockdownTest {

    /** Every write path {@code ModelController} exposes at {@code /{modelName}}. */
    private static final Set<String> GENERIC_WRITE_ENDPOINTS = Set.of(
            "createOne", "createOneAndFetch", "createList", "createListAndFetch",
            "updateOne", "updateOneAndFetch", "updateList", "updateListAndFetch", "updateByFilter",
            "deleteById", "deleteByIds", "deleteBySliceId",
            "copyById", "copyByIdAndFetch", "copyByIds", "copyByIdsAndFetch");

    @Test
    void periodController_shadowsEveryGenericWritePath() {
        assertThat(mappedPaths(TenantSubscriptionPeriodController.class))
                .as("unshadowed write endpoints would bypass the overlap guards and the projection refresh")
                .containsAll(GENERIC_WRITE_ENDPOINTS);
    }

    @Test
    void subscriptionController_shadowsEveryGenericWritePath() {
        // Authorization reads this row, so a hand-written subscriptionStatus / planId is a granted module, and a
        // hand-written projectedForDate defeats the staleness check that makes reading it safe.
        assertThat(mappedPaths(TenantSubscriptionController.class))
                .as("the subscription row is entirely projected — no external write is legitimate")
                .containsAll(GENERIC_WRITE_ENDPOINTS);
    }

    @Test
    void periodController_exposesTheGuardedWritesItReplacesThemWith() {
        // The point is not to block writing, it is to funnel it. These are the sanctioned ways in.
        assertThat(mappedPaths(TenantSubscriptionPeriodController.class))
                .contains("createOne", "updateOne", "deleteById", "deleteByIds", "changePlanNow");
    }

    /** Path names declared via {@code @PostMapping}, with the leading slash stripped. */
    private Set<String> mappedPaths(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .map(this::postMappingPaths)
                .flatMap(List::stream)
                .map(path -> path.startsWith("/") ? path.substring(1) : path)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private List<String> postMappingPaths(Method method) {
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        if (mapping == null) {
            return List.of();
        }
        String[] paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
        return List.of(paths);
    }
}
