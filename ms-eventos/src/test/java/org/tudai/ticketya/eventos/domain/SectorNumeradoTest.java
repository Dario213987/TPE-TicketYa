package org.tudai.ticketya.eventos.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SectorNumeradoTest {

    @Test
    void generarAsientos_creaTodasLasCombinacionesDeFilaYNumero() {
        SectorNumerado sector = SectorNumerado.builder().build();

        sector.generarAsientos(2, 3);

        assertThat(sector.getAsientos()).hasSize(6);
        Set<String> combinaciones = sector.getAsientos().stream()
                .map(asiento -> asiento.getFila() + "-" + asiento.getNumero())
                .collect(Collectors.toSet());
        assertThat(combinaciones).containsExactlyInAnyOrder(
                "1-1", "1-2", "1-3",
                "2-1", "2-2", "2-3");
    }

    @Test
    void addAsiento_yRemoveAsiento_actualizanLaColeccion() {
        SectorNumerado sector = SectorNumerado.builder().build();
        Asiento asiento = Asiento.builder().fila(1).numero(1).build();

        sector.addAsiento(asiento);
        assertThat(sector.getAsientos()).containsExactly(asiento);

        sector.removeEvento(asiento);
        assertThat(sector.getAsientos()).isEmpty();
    }
}
