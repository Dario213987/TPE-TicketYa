package org.tudai.ticketya.preferencias.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorTextoTest {

    @Test
    void normalizar_ignoraMayusculasYTildes() {
        assertThat(NormalizadorTexto.normalizar("Café Tacvba")).isEqualTo("cafe tacvba");
        assertThat(NormalizadorTexto.normalizar("CAFE TACVBA")).isEqualTo("cafe tacvba");
        assertThat(NormalizadorTexto.normalizar("Tándil")).isEqualTo("tandil");
        assertThat(NormalizadorTexto.normalizar("TANDIL")).isEqualTo("tandil");
    }

    @Test
    void normalizar_recortaEspaciosExterioresYConservaLosInteriores() {
        assertThat(NormalizadorTexto.normalizar("  Bahía   Blanca  ")).isEqualTo("bahia   blanca");
    }

    @Test
    void normalizar_conVacio_devuelveVacio() {
        assertThat(NormalizadorTexto.normalizar("   ")).isEmpty();
    }

    @Test
    void normalizar_conNull_devuelveNull() {
        assertThat(NormalizadorTexto.normalizar(null)).isNull();
    }
}
