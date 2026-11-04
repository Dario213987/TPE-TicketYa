package org.tudai.ticketya.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class CorrelationScopeTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void openNew_poneUnIdPropioEnElMdcYLoQuitaAlCerrar() {
        try (CorrelationScope scope = CorrelationScope.openNew()) {
            assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isEqualTo(scope.id());
            assertThat(CorrelationId.isValid(scope.id())).isTrue();
        }
        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull();
    }

    @Test
    void open_restauraElIdAnteriorAlCerrar() {
        MDC.put(MdcKeys.CORRELATION_ID, "externo");

        try (CorrelationScope scope = CorrelationScope.open("interno")) {
            assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isEqualTo("interno");
        }

        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isEqualTo("externo");
    }

    @Test
    void open_conIdInvalido_generaUnoNuevo() {
        try (CorrelationScope scope = CorrelationScope.open("a b")) {
            assertThat(scope.id()).isNotEqualTo("a b");
            assertThat(CorrelationId.isValid(scope.id())).isTrue();
        }
    }
}
