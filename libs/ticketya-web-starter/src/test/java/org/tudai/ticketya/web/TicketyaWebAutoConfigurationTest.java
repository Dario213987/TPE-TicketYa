package org.tudai.ticketya.web;

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
}