package io.softa.starter.permission.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.softa.starter.permission.spi.PermissionInfo;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.permission.spi.PermissionEndpointSource;
import io.softa.starter.permission.spi.PermissionSnapshotProvider;
import io.softa.starter.permission.spi.SensitiveFieldSetSource;
import io.softa.starter.permission.spi.support.DbPermissionEndpointSource;
import io.softa.starter.permission.spi.support.DbSensitiveFieldSetSource;
import io.softa.starter.permission.spi.support.DefaultPermissionSnapshotProvider;

/**
 * Standalone-fallback wiring for {@link PermissionStarterAutoConfiguration}.
 *
 * <p>No models are registered with {@code ModelManager} here, so the DB sources
 * return empty at the caches' {@code @PostConstruct} (no DB) and the context
 * boots — proving permission-starter can start with no {@code user-starter} on
 * the classpath. The second case registers competing SPI beans (as
 * {@code user-starter}'s impls would, registered before the auto-config just as
 * {@code @AutoConfigureBefore} arranges) and asserts each
 * {@code @ConditionalOnMissingBean} default backs off — no ambiguous double bean.
 *
 * <p>The stubs are plain classes with no stereotype so the auto-config's
 * {@code @ComponentScan("io.softa.starter.permission")} — which also sweeps this
 * test package — does not pick them up; they enter only via explicit
 * {@code withBean}.
 */
class PermissionStarterAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PermissionStarterAutoConfiguration.class))
            .withBean(ModelService.class, () -> mock(ModelService.class))
            .withBean(CacheService.class, () -> mock(CacheService.class));

    @Test
    void standalone_registersBuiltinDefaultSpis() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).getBean(PermissionSnapshotProvider.class)
                    .isInstanceOf(DefaultPermissionSnapshotProvider.class);
            assertThat(ctx).getBean(PermissionEndpointSource.class)
                    .isInstanceOf(DbPermissionEndpointSource.class);
            assertThat(ctx).getBean(SensitiveFieldSetSource.class)
                    .isInstanceOf(DbSensitiveFieldSetSource.class);
        });
    }

    @Test
    void userProvidedSpis_winOverBuiltinDefaults() {
        runner
                .withBean(PermissionSnapshotProvider.class, () -> new StubSnapshotProvider())
                .withBean(PermissionEndpointSource.class, () -> new StubEndpointSource())
                .withBean(SensitiveFieldSetSource.class, () -> new StubSensitiveSource())
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).getBeans(PermissionSnapshotProvider.class).hasSize(1);
                    assertThat(ctx).getBeans(PermissionEndpointSource.class).hasSize(1);
                    assertThat(ctx).getBeans(SensitiveFieldSetSource.class).hasSize(1);
                    assertThat(ctx).doesNotHaveBean(DefaultPermissionSnapshotProvider.class);
                    assertThat(ctx).doesNotHaveBean(DbPermissionEndpointSource.class);
                    assertThat(ctx).doesNotHaveBean(DbSensitiveFieldSetSource.class);
                });
    }

    // Plain (un-annotated) stubs — see class javadoc on why they must not be scannable.

    static class StubSnapshotProvider implements PermissionSnapshotProvider {
        @Override
        public PermissionInfo get(Long tenantId, Long userId) {
            return null;
        }
    }

    static class StubEndpointSource implements PermissionEndpointSource {
        @Override
        public List<PermissionEndpointDef> getPermissionEndpoints() {
            return List.of();
        }
    }

    static class StubSensitiveSource implements SensitiveFieldSetSource {
        @Override
        public List<SensitiveFieldSetDef> getSensitiveFieldSets() {
            return List.of();
        }
    }
}
