package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.Test;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.contract.eventos.model.EventoDetalleResponse;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventoAccessValidatorTest {

    private final EventoAccessValidator validator = new EventoAccessValidator();

    @Test
    void validarAcceso_cuandoElOrganizadorEsDuenio_noLanzaExcepcion() {
        assertThatCode(() -> validator.validarAcceso(evento(42L), 42L, false))
                .doesNotThrowAnyException();
    }

    @Test
    void validarAcceso_cuandoElOrganizadorNoEsDuenio_lanzaForbidden() {
        assertThatThrownBy(() -> validator.validarAcceso(evento(99L), 42L, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("permiso");
    }

    @Test
    void validarAcceso_cuandoEsAdmin_permiteEventoAjenoSinExcepcion() {
        assertThatCode(() -> validator.validarAcceso(evento(99L), 42L, true))
                .doesNotThrowAnyException();
    }

    private EventoDetalleResponse evento(Long organizadorId) {
        return new EventoDetalleResponse()
                .id(5L)
                .organizadorId(organizadorId);
    }
}
