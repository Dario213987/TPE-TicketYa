package org.tudai.ticketya.web;

import feign.Request;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;
import org.tudai.ticketya.web.feign.CorrelationIdFeignInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TicketyaWebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TicketyaWebAutoConfiguration.class));

    @Test
    void registrarHandlerGlobal() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(GlobalExceptionHandler.class));
    }

    @Test
    void registraLosDefaultsDeFeign() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ErrorDecoder.class);
            Request.Options options = ctx.getBean(Request.Options.class);
            assertThat(options.connectTimeoutMillis()).isEqualTo(2000);
            assertThat(options.readTimeoutMillis()).isEqualTo(5000);
        });
    }

    @Test
    void losTimeoutsSePuedenConfigurar() {
        runner.withPropertyValues("ticketya.feign.read-timeout-ms=1000")
                .run(ctx -> assertThat(ctx.getBean(Request.Options.class).readTimeoutMillis()).isEqualTo(1000));
    }

    @Test
    void registraElUserContextPorHeaders() {
        runner.run(ctx -> {
            assertThat(ctx).hasBean("userContext");
            assertThat(ctx.getBean(UserContext.class)).isNotNull();
        });
    }

    @Test
    void unUserContextPropioReemplazaAlDelStarter() {
        runner.withBean(UserContext.class, () -> mock(UserContext.class))
                .run(ctx -> {
                    assertThat(ctx.getBeanNamesForType(UserContext.class)).hasSize(1);
                    assertThat(ctx.containsBean("scopedTarget.userContext")).isFalse();
                });
    }

    @Test
    void registraElInterceptorDeAutorizacion() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(org.tudai.ticketya.web.security.AuthorizeInterceptor.class);
            assertThat(ctx).hasBean("authorizeWebMvcConfigurer");
        });
    }

    @Test
    void registraElFiltroDeCorrelationIdPrimeroYSuInterceptorFeign() {
        runner.run(ctx -> {
            FilterRegistrationBean<?> registration =
                    ctx.getBean("correlationIdFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
            assertThat(ctx).hasSingleBean(CorrelationIdFeignInterceptor.class);
        });
    }

    @Test
    void registraElFiltroDeLogDeRequestsDespuesDelCorrelationIdYDentroDelContextoDeSpring() {
        runner.run(ctx -> {
            FilterRegistrationBean<?> registration =
                    ctx.getBean("requestLoggingFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.getOrder()).isGreaterThan(Ordered.HIGHEST_PRECEDENCE + 10);
            assertThat(registration.getOrder()).isGreaterThan(-105);   // orden del RequestContextFilter de Spring
        });
    }

    @Test
    void elLogDeRequestsSePuedeDesactivar() {
        runner.withPropertyValues("ticketya.logging.requests.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("requestLoggingFilterRegistration"));
    }
}