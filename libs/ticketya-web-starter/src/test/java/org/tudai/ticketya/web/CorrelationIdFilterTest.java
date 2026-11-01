package org.tudai.ticketya.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.tudai.ticketya.web.correlation.CorrelationIdFilter;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void sinHeader_generaUnIdYLoPoneEnElMdcYEnLaRespuesta() throws Exception {
        AtomicReference<String> visto = new AtomicReference<>();

        filter.doFilter(request, response, (rq, rs) -> visto.set(MDC.get("correlationId")));

        String id = response.getHeader("X-Correlation-Id");
        assertThat(id).isNotBlank();
        assertThat(visto.get()).isEqualTo(id);
    }

    @Test
    void conHeaderValido_loConserva() throws Exception {
        request.addHeader("X-Correlation-Id", "mi-id-123");
        AtomicReference<String> visto = new AtomicReference<>();

        filter.doFilter(request, response, (rq, rs) -> visto.set(MDC.get("correlationId")));

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("mi-id-123");
        assertThat(visto.get()).isEqualTo("mi-id-123");
    }

    @Test
    void conHeaderInvalido_loReemplaza() throws Exception {
        request.addHeader("X-Correlation-Id", "a b\nc");

        filter.doFilter(request, response, (rq, rs) -> { });

        assertThat(response.getHeader("X-Correlation-Id")).isNotEqualTo("a b\nc").isNotBlank();
    }

    @Test
    void alTerminar_limpiaElMdc() throws Exception {
        filter.doFilter(request, response, (rq, rs) -> { });

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void limpiaElMdcAunqueLaCadenaFalle() {
        assertThatThrownBy(() -> filter.doFilter(request, response, (rq, rs) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get("correlationId")).isNull();
    }
}
