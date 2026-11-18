package org.tudai.ticketya.notificaciones.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReintentosNotificacionPropertiesTest {

    @Test
    void valoresPorDefecto() {
        ReintentosNotificacionProperties propiedades = new ReintentosNotificacionProperties();

        assertThat(propiedades.getMaxIntentos()).isEqualTo(8);
        assertThat(propiedades.getIntervaloInicialMs()).isEqualTo(5000L);
        assertThat(propiedades.getMultiplicador()).isEqualTo(2.0);
        assertThat(propiedades.getIntervaloMaximoMs()).isEqualTo(300000L);
    }

    @Test
    void permiteSobrescribirValores() {
        ReintentosNotificacionProperties propiedades = new ReintentosNotificacionProperties();
        propiedades.setMaxIntentos(3);
        propiedades.setIntervaloInicialMs(1000L);
        propiedades.setMultiplicador(1.5);
        propiedades.setIntervaloMaximoMs(10000L);

        assertThat(propiedades.getMaxIntentos()).isEqualTo(3);
        assertThat(propiedades.getIntervaloInicialMs()).isEqualTo(1000L);
        assertThat(propiedades.getMultiplicador()).isEqualTo(1.5);
        assertThat(propiedades.getIntervaloMaximoMs()).isEqualTo(10000L);
    }
}
