package org.tudai.ticketya.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class CorrelationIdTest {

    @Test
    void generate_devuelveUnIdValidoYDistintoCadaVez() {
        String a = CorrelationId.generate();
        String b = CorrelationId.generate();

        assertThat(CorrelationId.isValid(a)).isTrue();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void isValid_aceptaLosCaracteresPermitidosHastaElLimite() {
        assertThat(CorrelationId.isValid("abc-123_X.y")).isTrue();
        assertThat(CorrelationId.isValid("a".repeat(64))).isTrue();
    }

    @Test
    void isValid_rechazaLoDemas() {
        assertThat(CorrelationId.isValid(null)).isFalse();
        assertThat(CorrelationId.isValid("")).isFalse();
        assertThat(CorrelationId.isValid("a b")).isFalse();
        assertThat(CorrelationId.isValid("a\nb")).isFalse();
        assertThat(CorrelationId.isValid("<script>")).isFalse();
        assertThat(CorrelationId.isValid("a".repeat(65))).isFalse();
    }

    @Test
    void resolve_conservaElValidoYReemplazaElInvalido() {
        assertThat(CorrelationId.resolve("mi-id-1")).isEqualTo("mi-id-1");
        assertThat(CorrelationId.resolve("a b")).isNotEqualTo("a b");
        assertThat(CorrelationId.isValid(CorrelationId.resolve(null))).isTrue();
    }
}