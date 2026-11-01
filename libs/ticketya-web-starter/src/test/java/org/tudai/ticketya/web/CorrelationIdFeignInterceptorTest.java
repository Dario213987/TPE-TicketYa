package org.tudai.ticketya.web;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.tudai.ticketya.web.feign.CorrelationIdFeignInterceptor;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class CorrelationIdFeignInterceptorTest {

    private final CorrelationIdFeignInterceptor interceptor = new CorrelationIdFeignInterceptor();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void copiaElIdDelMdcALaLlamadaSaliente() {
        MDC.put("correlationId", "abc-123");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-Correlation-Id")).containsExactly("abc-123");
    }

    @Test
    void sinIdEnElMdc_noAgregaNada() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("X-Correlation-Id");
    }
}
