package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.tickets.config.QrProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrFirmaServiceTest {

    private QrProperties properties;
    private QrFirmaService service;

    @BeforeEach
    void setUp() {
        properties = new QrProperties();
        properties.setSecreto("secreto-de-prueba-con-longitud-suficiente");
        service = new QrFirmaService(properties);
    }

    @Test
    void firmar_devuelveUnCodigoVerificableConElPayloadOriginal() {
        String codigo = service.firmar("ticket-123");

        assertThat(codigo).startsWith("ticket-123.");
        assertThat(service.verificar(codigo)).isEqualTo("ticket-123");
    }

    @Test
    void verificar_rechazaUnCodigoSinFirma() {
        assertThatThrownBy(() -> service.verificar("ticket-123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("firma");
    }

    @Test
    void verificar_rechazaUnaFirmaManipulada() {
        String codigo = service.firmar("ticket-123");
        String manipulado = codigo.substring(0, codigo.length() - 1)
                + (codigo.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> service.verificar(manipulado))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("firma");
    }

    @Test
    void verificar_rechazaUnaFirmaGeneradaConOtroSecreto() {
        QrProperties otros = new QrProperties();
        otros.setSecreto("otro-secreto-de-prueba-con-longitud-suficiente");
        String codigo = new QrFirmaService(otros).firmar("ticket-123");

        assertThatThrownBy(() -> service.verificar(codigo))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("firma");
    }

    @Test
    void firmar_sinSecretoConfigurado_lanzaIllegalState() {
        properties.setSecreto(" ");

        assertThatThrownBy(() -> service.firmar("ticket-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TICKETS_QR_SECRET");
    }
}
