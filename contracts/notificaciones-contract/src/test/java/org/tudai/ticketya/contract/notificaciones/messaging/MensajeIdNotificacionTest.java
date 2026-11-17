package org.tudai.ticketya.contract.notificaciones.messaging;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class MensajeIdNotificacionTest {

    private static final Pattern FORMATO = Pattern.compile(
            "^MSE-\\d+-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Test
    void generarDevuelveFormatoEsperado() {
        String id = MensajeIdNotificacion.generar("MSE");
        assertTrue(FORMATO.matcher(id).matches(), "Formato inesperado: " + id);
    }

    @Test
    void generarDosLlamadasDevuelvenIdsDistintos() {
        String a = MensajeIdNotificacion.generar("MSE");
        String b = MensajeIdNotificacion.generar("MSE");
        assertNotEquals(a, b);
    }

    @Test
    void generarConPrefijoInvalidoLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> MensajeIdNotificacion.generar(null));
        assertThrows(IllegalArgumentException.class, () -> MensajeIdNotificacion.generar(""));
        assertThrows(IllegalArgumentException.class, () -> MensajeIdNotificacion.generar("   "));
    }

    @Test
    void generarDerivadoDevuelveIdEstablePorEntidad() {
        assertEquals("MST-RESERVA-99", MensajeIdNotificacion.generarDerivado("MST", "RESERVA", 99L));
        assertEquals(MensajeIdNotificacion.generarDerivado("MST", "RESERVA", 99L),
                MensajeIdNotificacion.generarDerivado("MST", "RESERVA", 99L));
    }

    @Test
    void generarDerivadoConDatosInvalidosLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> MensajeIdNotificacion.generarDerivado(null, "RESERVA", 1L));
        assertThrows(IllegalArgumentException.class, () -> MensajeIdNotificacion.generarDerivado("MST", " ", 1L));
        assertThrows(IllegalArgumentException.class, () -> MensajeIdNotificacion.generarDerivado("MST", "RESERVA", null));
    }
}
