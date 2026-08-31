package io.softa.starter.message.shared;

import io.softa.framework.orm.domain.FilterUnit;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.starter.message.mail.entity.MailReceiveServerConfig;
import io.softa.starter.message.mail.entity.MailSendServerConfig;
import io.softa.starter.message.mail.service.impl.MailReceiveServerConfigServiceImpl;
import io.softa.starter.message.mail.service.impl.MailSendServerConfigServiceImpl;
import io.softa.starter.message.sms.entity.SmsTemplate;
import io.softa.starter.message.sms.entity.SmsTemplateProviderBinding;
import io.softa.starter.message.sms.service.impl.SmsTemplateProviderBindingServiceImpl;
import io.softa.starter.message.sms.service.impl.SmsTemplateServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Every read that resolves the platform tier has to ask for the tenant id the tier actually lives at.
 *
 * <p>This is a regression guard for a failure that already happened. Four of these methods carried a
 * hardcoded {@code 0L} after the tier had been named as a constant, and the whole suite stayed green
 * through it — the dispatcher tests mock {@code findPlatformDefault()} wholesale to check precedence,
 * so nothing ever looked inside at the filter it builds. The symptom is not an exception: a platform
 * operator's default server sits at one id, the fallback query asks for another, and every tenant
 * without a config of its own gets a permanent, silent "no platform config".
 *
 * <p>So these assert the filter, not the result. A test that stubs the query and checks the row that
 * comes back passes at any tenant id, which is precisely the hole that let it through.
 */
class PlatformTierLookupTest {

    /** The tenantId value a captured Filters asks for, or null when it constrains no tenant. */
    private static Long tenantAskedFor(Filters filters) {
        AtomicReference<Long> seen = new AtomicReference<>();
        walk(filters, unit -> {
            if ("tenantId".equals(unit.getField())) {
                Object value = unit.getValue();
                seen.set(value instanceof Number n ? n.longValue() : null);
            }
        });
        return seen.get();
    }

    private static void walk(Filters filters, Consumer<FilterUnit> visit) {
        if (filters == null) {
            return;
        }
        if (filters.getFilterUnit() != null) {
            visit.accept(filters.getFilterUnit());
        }
        if (filters.getChildren() != null) {
            filters.getChildren().forEach(child -> walk(child, visit));
        }
    }

    @Test
    void mailSendPlatformDefault() {
        MailSendServerConfigServiceImpl service = Mockito.spy(new MailSendServerConfigServiceImpl());
        AtomicReference<Filters> captured = new AtomicReference<>();
        doAnswer(inv -> {
            captured.set(((FlexQuery) inv.getArgument(0)).getFilters());
            return List.<MailSendServerConfig>of();
        }).when(service).searchList(any(FlexQuery.class));

        service.findPlatformDefault();

        assertThat(tenantAskedFor(captured.get()))
                .as("this is the fallback a tenant with no send server of its own is given")
                .isEqualTo(TenantScopes.PLATFORM);
    }

    @Test
    void mailReceivePlatformDefault() {
        MailReceiveServerConfigServiceImpl service = Mockito.spy(new MailReceiveServerConfigServiceImpl());
        AtomicReference<Filters> captured = new AtomicReference<>();
        doAnswer(inv -> {
            captured.set(((FlexQuery) inv.getArgument(0)).getFilters());
            return List.<MailReceiveServerConfig>of();
        }).when(service).searchList(any(FlexQuery.class));

        service.findPlatformDefault();

        assertThat(tenantAskedFor(captured.get())).isEqualTo(TenantScopes.PLATFORM);
    }

    @Test
    void smsTemplateByCode() {
        SmsTemplateServiceImpl service = Mockito.spy(new SmsTemplateServiceImpl());
        AtomicReference<Filters> captured = new AtomicReference<>();
        doAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return Optional.<SmsTemplate>empty();
        }).when(service).searchOne(any(Filters.class));

        service.findPlatformByCode("any.code");

        assertThat(tenantAskedFor(captured.get())).isEqualTo(TenantScopes.PLATFORM);
    }

    @Test
    void smsProviderBindings() {
        SmsTemplateProviderBindingServiceImpl service =
                Mockito.spy(new SmsTemplateProviderBindingServiceImpl());
        AtomicReference<Filters> captured = new AtomicReference<>();
        doAnswer(inv -> {
            captured.set(((FlexQuery) inv.getArgument(0)).getFilters());
            return List.<SmsTemplateProviderBinding>of();
        }).when(service).searchList(any(FlexQuery.class));

        service.findPlatformBindingsByTemplateId(1L);

        assertThat(tenantAskedFor(captured.get())).isEqualTo(TenantScopes.PLATFORM);
    }
}
