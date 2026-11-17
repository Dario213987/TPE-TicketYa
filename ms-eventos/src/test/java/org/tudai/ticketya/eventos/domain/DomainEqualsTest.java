package org.tudai.ticketya.eventos.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEqualsTest {

    @Test
    void lugar_soloEsIgualAOtroLugarConElMismoId() {
        Lugar lugar = Lugar.builder().id(1L).nombre("Estadio").build();
        Lugar mismoId = Lugar.builder().id(1L).nombre("Otro nombre").build();
        Lugar otroId = Lugar.builder().id(2L).nombre("Estadio").build();

        assertThat(lugar).isEqualTo(mismoId);
        assertThat(mismoId).isEqualTo(lugar);
        assertThat(lugar).isNotEqualTo(otroId);
        assertThat(lugar).isNotEqualTo(Participante.builder().id(1L).build());
        assertThat(lugar).isNotEqualTo(null);
    }

    @Test
    void evento_soloEsIgualAOtroEventoConElMismoId() {
        Evento evento = Evento.builder().id(1L).nombre("Festival").build();
        Evento mismoId = Evento.builder().id(1L).nombre("Otro nombre").build();
        Evento otroId = Evento.builder().id(2L).nombre("Festival").build();

        assertThat(evento).isEqualTo(mismoId);
        assertThat(mismoId).isEqualTo(evento);
        assertThat(evento).isNotEqualTo(otroId);
        assertThat(evento).isNotEqualTo(Participante.builder().id(1L).build());
    }

    @Test
    void sector_soloEsIgualAOtroSectorConElMismoId() {
        Sector sector = SectorNumerado.builder().id(1L).nombre("Platea").build();
        Sector mismoId = SectorNoNumerado.builder().id(1L).nombre("Campo").build();
        Sector otroId = SectorNumerado.builder().id(2L).nombre("Platea").build();

        assertThat(sector).isEqualTo(mismoId);
        assertThat(mismoId).isEqualTo(sector);
        assertThat(sector).isNotEqualTo(otroId);
        assertThat(sector).isNotEqualTo(Participante.builder().id(1L).build());
    }

    @Test
    void losSectoresConElMismoIdNoSeDuplicanEnUnSet() {
        Set<Sector> sectores = new HashSet<>();
        sectores.add(SectorNumerado.builder().id(1L).build());
        sectores.add(SectorNoNumerado.builder().id(1L).build());

        assertThat(sectores).hasSize(1);
    }
}
