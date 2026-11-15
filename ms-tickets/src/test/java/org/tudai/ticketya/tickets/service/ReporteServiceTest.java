package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.tickets.client.EventoClient;
import org.tudai.ticketya.tickets.client.dto.EventoResponseDto;
import org.tudai.ticketya.tickets.client.dto.SectorResponseDto;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.dto.ReporteEntradasVendidasDTO;
import org.tudai.ticketya.tickets.repository.ReporteRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    private static final LocalDateTime FECHA_EVENTO = LocalDateTime.of(2026, 12, 1, 21, 0);

    @Mock
    private EventoClient eventoClient;

    @Mock
    private ReporteRepository reporteRepository;

    @InjectMocks
    private ReporteService service;

    @Test
    void obtenerReporte_cuentaVendidasYDevueltasPorSectorYCompletaSectoresSinMovimientos() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 42L,
                sector(10L, "Platea"), sector(20L, "Campo"), sector(30L, "Pullman")));
        when(reporteRepository.resumirPorSector(
                5L,
                List.of(EstadoTicket.EMITIDO, EstadoTicket.USADO),
                EstadoTicket.DEVUELTO,
                List.of(EstadoTicket.EMITIDO, EstadoTicket.USADO, EstadoTicket.DEVUELTO)))
                .thenReturn(List.<Object[]>of(resumen(10L, 2, 1)));

        ReporteEntradasVendidasDTO reporte = service.obtenerReporteEntradasVendidas(5L, 42L, false);

        assertThat(reporte.getEventoId()).isEqualTo(5L);
        assertThat(reporte.getEventoNombre()).isEqualTo("Recital");
        assertThat(reporte.getEventoFecha()).isEqualTo(FECHA_EVENTO);
        assertThat(reporte.getEventoEstado()).isEqualTo("PUBLICADO");
        assertThat(reporte.getEntradasVendidas()).isEqualTo(2);
        assertThat(reporte.getEntradasDevueltas()).isEqualTo(1);
        assertThat(reporte.getSectores()).hasSize(3);
        assertThat(reporte.getSectores().get(0).getSectorId()).isEqualTo(10L);
        assertThat(reporte.getSectores().get(0).getSectorNombre()).isEqualTo("Platea");
        assertThat(reporte.getSectores().get(0).getEntradasVendidas()).isEqualTo(2);
        assertThat(reporte.getSectores().get(0).getEntradasDevueltas()).isEqualTo(1);
        assertThat(reporte.getSectores().get(1).getEntradasVendidas()).isZero();
        assertThat(reporte.getSectores().get(1).getEntradasDevueltas()).isZero();
        assertThat(reporte.getSectores().get(2).getSectorId()).isEqualTo(30L);
        assertThat(reporte.getSectores().get(2).getEntradasVendidas()).isZero();
        assertThat(reporte.getSectores().get(2).getEntradasDevueltas()).isZero();

        verify(reporteRepository).resumirPorSector(
                5L,
                List.of(EstadoTicket.EMITIDO, EstadoTicket.USADO),
                EstadoTicket.DEVUELTO,
                List.of(EstadoTicket.EMITIDO, EstadoTicket.USADO, EstadoTicket.DEVUELTO));
    }

    @Test
    void obtenerReporte_cuandoElOrganizadorNoEsDuenio_lanzaForbidden() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 99L));

        assertThatThrownBy(() -> service.obtenerReporteEntradasVendidas(5L, 42L, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("permiso");

        verifyNoInteractions(reporteRepository);
    }

    @Test
    void obtenerReporte_cuandoEsAdmin_consultaEventoAjenoSinTickets() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 99L,
                sector(10L, "Platea")));
        when(reporteRepository.resumirPorSector(
                5L,
                List.of(EstadoTicket.EMITIDO, EstadoTicket.USADO),
                EstadoTicket.DEVUELTO,
                List.of(EstadoTicket.EMITIDO, EstadoTicket.USADO, EstadoTicket.DEVUELTO)))
                .thenReturn(List.of());

        ReporteEntradasVendidasDTO reporte = service.obtenerReporteEntradasVendidas(5L, 42L, true);

        assertThat(reporte.getEntradasVendidas()).isZero();
        assertThat(reporte.getEntradasDevueltas()).isZero();
        assertThat(reporte.getSectores()).hasSize(1);
        assertThat(reporte.getSectores().get(0).getEntradasVendidas()).isZero();
    }

    private EventoResponseDto evento(Long id, String nombre, Long organizadorId,
                                     SectorResponseDto... sectores) {
        return EventoResponseDto.builder()
                .id(id)
                .nombre(nombre)
                .fecha(FECHA_EVENTO)
                .estado("PUBLICADO")
                .organizadorId(organizadorId)
                .sectores(List.of(sectores))
                .build();
    }

    private SectorResponseDto sector(Long id, String nombre) {
        return SectorResponseDto.builder()
                .id(id)
                .nombre(nombre)
                .build();
    }

    private Object[] resumen(Long sectorId, long vendidas, long devueltas) {
        return new Object[]{sectorId, vendidas, devueltas};
    }
}
