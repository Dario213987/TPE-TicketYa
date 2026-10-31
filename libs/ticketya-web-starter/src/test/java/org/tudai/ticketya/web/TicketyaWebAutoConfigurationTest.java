package org.tudai.ticketya.web;

import feign.Request;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

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
}