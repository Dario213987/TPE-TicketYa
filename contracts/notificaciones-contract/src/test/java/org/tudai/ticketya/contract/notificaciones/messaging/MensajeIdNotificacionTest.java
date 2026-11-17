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
    void generarDerivadoEsEstableParaMismoMotivoYEvento() {
        String a = MensajeIdNotificacion.generarDerivado("MSE", MotivoNotificacion.CANCELACION, 10L);
        String b = MensajeIdNotificacion.generarDerivado("MSE", MotivoNotificacion.CANCELACION, 10L);

        assertEquals(a, b);
        assertEquals("MSE-CANCELACION-10", a);
    }

    @Test
    void generarDerivadoCambiaConMotivoOEvento() {
        String base = MensajeIdNotificacion.generarDerivado("MSE", MotivoNotificacion.CANCELACION, 10L);
        String otroMotivo = MensajeIdNotificacion.generarDerivado("MSE", MotivoNotificacion.SUSPENSION, 10L);
        String otroEvento = MensajeIdNotificacion.generarDerivado("MSE", MotivoNotificacion.CANCELACION, 11L);

        assertNotEquals(base, otroMotivo);
        assertNotEquals(base, otroEvento);
    }

    @Test
    void generarDerivadoValidaArgumentos() {
        assertThrows(IllegalArgumentException.class,
                () -> MensajeIdNotificacion.generarDerivado(null, MotivoNotificacion.CANCELACION, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> MensajeIdNotificacion.generarDerivado("MSE", null, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> MensajeIdNotificacion.generarDerivado("MSE", MotivoNotificacion.CANCELACION, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> MensajeIdNotificacion.generarDerivado("MST", "RESERVA", null));
        assertThrows(IllegalArgumentException.class,
                () -> MensajeIdNotificacion.generarDerivado("MST", " ", 10L));
    }

    @Test
    void generarDerivadoPorEntidadEsEstable() {
        assertEquals("MST-RESERVA-77",
                MensajeIdNotificacion.generarDerivado("MST", "RESERVA", 77L));
        assertEquals(MensajeIdNotificacion.generarDerivado("MST", "RESERVA", 77L),
                MensajeIdNotificacion.generarDerivado("MST", "RESERVA", 77L));
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
