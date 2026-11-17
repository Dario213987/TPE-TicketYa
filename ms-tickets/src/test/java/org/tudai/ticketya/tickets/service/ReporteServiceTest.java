package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.contract.eventos.api.EventosApiClient;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.EventoDetalleResponse;
import org.tudai.ticketya.contract.eventos.model.SectorResponse;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.dto.reporte.ReporteEntradasVendidasDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteOcupacionDTO;
import org.tudai.ticketya.tickets.dto.reporte.ReporteRecaudacionDTO;
import org.tudai.ticketya.tickets.repository.DisponibilidadSectorRepository;
import org.tudai.ticketya.tickets.repository.ReporteRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    private static final LocalDateTime FECHA_EVENTO = LocalDateTime.of(2026, 12, 1, 21, 0);

    private static final List<EstadoTicket> ESTADOS_VENDIDOS =
            List.of(EstadoTicket.EMITIDO, EstadoTicket.VENDIDO, EstadoTicket.USADO);

    private static final List<EstadoTicket> ESTADOS_REPORTE_ENTRADAS =
            List.of(EstadoTicket.EMITIDO, EstadoTicket.VENDIDO, EstadoTicket.USADO, EstadoTicket.DEVUELTO);

    @Mock
    private EventosApiClient eventoClient;

    @Spy
    private EventoAccessValidator eventoAccessValidator = new EventoAccessValidator();

    @Mock
    private ReporteRepository reporteRepository;

    @Mock
    private DisponibilidadSectorRepository disponibilidadSectorRepository;

    @InjectMocks
    private ReporteService service;

    @Test
    void obtenerReporte_cuentaVendidasYDevueltasPorSectorYCompletaSectoresSinMovimientos() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 42L,
                sector(10L, "Platea"), sector(20L, "Campo"), sector(30L, "Pullman")));
        when(reporteRepository.resumirPorSector(
                5L,
                ESTADOS_VENDIDOS,
                EstadoTicket.DEVUELTO,
                ESTADOS_REPORTE_ENTRADAS))
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
                ESTADOS_VENDIDOS,
                EstadoTicket.DEVUELTO,
                ESTADOS_REPORTE_ENTRADAS);
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
    void obtenerReporte_cuandoElEventoNoExiste_lanzaNotFound() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.obtenerReporteEntradasVendidas(5L, 42L, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("5");
        assertThatThrownBy(() -> service.obtenerReporteOcupacion(5L, 42L, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("5");
        assertThatThrownBy(() -> service.obtenerReporteRecaudacion(5L, 42L, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("5");

        verifyNoInteractions(reporteRepository, disponibilidadSectorRepository);
    }

    @Test
    void obtenerReporte_cuandoEsAdmin_consultaEventoAjenoSinTickets() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 99L,
                sector(10L, "Platea")));
        when(reporteRepository.resumirPorSector(
                5L,
                ESTADOS_VENDIDOS,
                EstadoTicket.DEVUELTO,
                ESTADOS_REPORTE_ENTRADAS))
                .thenReturn(List.of());

        ReporteEntradasVendidasDTO reporte = service.obtenerReporteEntradasVendidas(5L, 42L, true);

        assertThat(reporte.getEntradasVendidas()).isZero();
        assertThat(reporte.getEntradasDevueltas()).isZero();
        assertThat(reporte.getSectores()).hasSize(1);
        assertThat(reporte.getSectores().get(0).getEntradasVendidas()).isZero();
    }

    @Test
    void obtenerOcupacion_calculaPorcentajePorSectorYTotalPonderado() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 42L,
                sector(10L, "Platea"), sector(20L, "Campo"), sector(30L, "Pullman")));
        when(disponibilidadSectorRepository.findByEvento_EventoIdOrderByIdAsc(5L))
                .thenReturn(List.of(disponibilidadSector(10L, 100), disponibilidadSector(20L, 50)));
        when(reporteRepository.resumirPorSector(
                5L,
                ESTADOS_VENDIDOS,
                EstadoTicket.DEVUELTO,
                ESTADOS_REPORTE_ENTRADAS))
                .thenReturn(List.<Object[]>of(resumen(10L, 30, 0), resumen(20L, 10, 0)));

        ReporteOcupacionDTO reporte = service.obtenerReporteOcupacion(5L, 42L, false);

        assertThat(reporte.getEventoId()).isEqualTo(5L);
        assertThat(reporte.getEventoNombre()).isEqualTo("Recital");
        assertThat(reporte.getEventoFecha()).isEqualTo(FECHA_EVENTO);
        assertThat(reporte.getEventoEstado()).isEqualTo("PUBLICADO");
        assertThat(reporte.getOcupacionTotal()).isEqualTo(26.67);
        assertThat(reporte.getSectores()).hasSize(3);
        assertThat(reporte.getSectores().get(0).getSectorId()).isEqualTo(10L);
        assertThat(reporte.getSectores().get(0).getSectorNombre()).isEqualTo("Platea");
        assertThat(reporte.getSectores().get(0).getOcupacion()).isEqualTo(30.0);
        assertThat(reporte.getSectores().get(1).getOcupacion()).isEqualTo(20.0);
        assertThat(reporte.getSectores().get(2).getSectorId()).isEqualTo(30L);
        assertThat(reporte.getSectores().get(2).getOcupacion()).isZero();
    }

    @Test
    void obtenerOcupacion_cuandoElOrganizadorNoEsDuenio_lanzaForbidden() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 99L));

        assertThatThrownBy(() -> service.obtenerReporteOcupacion(5L, 42L, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("permiso");

        verifyNoInteractions(reporteRepository, disponibilidadSectorRepository);
    }

    @Test
    void obtenerOcupacion_cuandoEsAdmin_consultaEventoAjenoSinTickets() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 99L,
                sector(10L, "Platea")));
        when(disponibilidadSectorRepository.findByEvento_EventoIdOrderByIdAsc(5L))
                .thenReturn(List.of(disponibilidadSector(10L, 100)));
        when(reporteRepository.resumirPorSector(
                5L,
                ESTADOS_VENDIDOS,
                EstadoTicket.DEVUELTO,
                ESTADOS_REPORTE_ENTRADAS))
                .thenReturn(List.of());

        ReporteOcupacionDTO reporte = service.obtenerReporteOcupacion(5L, 42L, true);

        assertThat(reporte.getOcupacionTotal()).isZero();
        assertThat(reporte.getSectores()).hasSize(1);
        assertThat(reporte.getSectores().get(0).getOcupacion()).isZero();
    }

    @Test
    void obtenerOcupacion_sinCapacidadConocida_devuelveCero() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 42L,
                sector(10L, "Platea")));
        when(disponibilidadSectorRepository.findByEvento_EventoIdOrderByIdAsc(5L))
                .thenReturn(List.of());
        when(reporteRepository.resumirPorSector(
                5L,
                ESTADOS_VENDIDOS,
                EstadoTicket.DEVUELTO,
                ESTADOS_REPORTE_ENTRADAS))
                .thenReturn(List.<Object[]>of(resumen(10L, 5, 0)));

        ReporteOcupacionDTO reporte = service.obtenerReporteOcupacion(5L, 42L, false);

        assertThat(reporte.getOcupacionTotal()).isZero();
        assertThat(reporte.getSectores().get(0).getOcupacion()).isZero();
    }

    @Test
    void obtenerRecaudacion_calculaVentasReintegrosYNetoPorSectorYTotal() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 42L,
                sector(10L, "Platea"), sector(20L, "Campo"), sector(30L, "Pullman")));
        when(reporteRepository.resumirRecaudacionPorSector(
                5L,
                ESTADOS_REPORTE_ENTRADAS,
                EstadoTicket.DEVUELTO))
                .thenReturn(List.<Object[]>of(
                        resumenRecaudacion(10L, 300.0, 100.0),
                        resumenRecaudacion(20L, 50.0, 0.0)));

        ReporteRecaudacionDTO reporte = service.obtenerReporteRecaudacion(5L, 42L, false);

        assertThat(reporte.getEventoId()).isEqualTo(5L);
        assertThat(reporte.getEventoNombre()).isEqualTo("Recital");
        assertThat(reporte.getEventoFecha()).isEqualTo(FECHA_EVENTO);
        assertThat(reporte.getEventoEstado()).isEqualTo("PUBLICADO");
        assertThat(reporte.getVentas()).isEqualTo(350.0);
        assertThat(reporte.getReintegros()).isEqualTo(100.0);
        assertThat(reporte.getRecaudacionTotal()).isEqualTo(250.0);
        assertThat(reporte.getSectores()).hasSize(3);
        assertThat(reporte.getSectores().get(0).getSectorId()).isEqualTo(10L);
        assertThat(reporte.getSectores().get(0).getSectorNombre()).isEqualTo("Platea");
        assertThat(reporte.getSectores().get(0).getVentas()).isEqualTo(300.0);
        assertThat(reporte.getSectores().get(0).getReintegros()).isEqualTo(100.0);
        assertThat(reporte.getSectores().get(0).getRecaudacion()).isEqualTo(200.0);
        assertThat(reporte.getSectores().get(1).getVentas()).isEqualTo(50.0);
        assertThat(reporte.getSectores().get(1).getReintegros()).isZero();
        assertThat(reporte.getSectores().get(1).getRecaudacion()).isEqualTo(50.0);
        assertThat(reporte.getSectores().get(2).getSectorId()).isEqualTo(30L);
        assertThat(reporte.getSectores().get(2).getVentas()).isZero();
        assertThat(reporte.getSectores().get(2).getReintegros()).isZero();
        assertThat(reporte.getSectores().get(2).getRecaudacion()).isZero();

        verify(reporteRepository).resumirRecaudacionPorSector(
                5L,
                ESTADOS_REPORTE_ENTRADAS,
                EstadoTicket.DEVUELTO);
    }

    @Test
    void obtenerRecaudacion_redondeaMontosADosDecimales() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 42L,
                sector(10L, "Platea")));
        when(reporteRepository.resumirRecaudacionPorSector(
                5L,
                ESTADOS_REPORTE_ENTRADAS,
                EstadoTicket.DEVUELTO))
                .thenReturn(List.<Object[]>of(resumenRecaudacion(10L, 33.333, 11.111)));

        ReporteRecaudacionDTO reporte = service.obtenerReporteRecaudacion(5L, 42L, false);

        assertThat(reporte.getSectores().get(0).getVentas()).isEqualTo(33.33);
        assertThat(reporte.getSectores().get(0).getReintegros()).isEqualTo(11.11);
        assertThat(reporte.getSectores().get(0).getRecaudacion()).isEqualTo(22.22);
        assertThat(reporte.getVentas()).isEqualTo(33.33);
        assertThat(reporte.getReintegros()).isEqualTo(11.11);
        assertThat(reporte.getRecaudacionTotal()).isEqualTo(22.22);
    }

    @Test
    void obtenerRecaudacion_sinTickets_devuelveCeros() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 42L,
                sector(10L, "Platea")));
        when(reporteRepository.resumirRecaudacionPorSector(
                5L,
                ESTADOS_REPORTE_ENTRADAS,
                EstadoTicket.DEVUELTO))
                .thenReturn(List.of());

        ReporteRecaudacionDTO reporte = service.obtenerReporteRecaudacion(5L, 42L, false);

        assertThat(reporte.getVentas()).isZero();
        assertThat(reporte.getReintegros()).isZero();
        assertThat(reporte.getRecaudacionTotal()).isZero();
        assertThat(reporte.getSectores()).hasSize(1);
        assertThat(reporte.getSectores().get(0).getVentas()).isZero();
        assertThat(reporte.getSectores().get(0).getReintegros()).isZero();
        assertThat(reporte.getSectores().get(0).getRecaudacion()).isZero();
    }

    @Test
    void obtenerRecaudacion_cuandoElOrganizadorNoEsDuenio_lanzaForbidden() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 99L));

        assertThatThrownBy(() -> service.obtenerReporteRecaudacion(5L, 42L, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("permiso");

        verifyNoInteractions(reporteRepository);
    }

    @Test
    void obtenerRecaudacion_cuandoEsAdmin_consultaEventoAjenoSinTickets() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 99L,
                sector(10L, "Platea")));
        when(reporteRepository.resumirRecaudacionPorSector(
                5L,
                ESTADOS_REPORTE_ENTRADAS,
                EstadoTicket.DEVUELTO))
                .thenReturn(List.of());

        ReporteRecaudacionDTO reporte = service.obtenerReporteRecaudacion(5L, 42L, true);

        assertThat(reporte.getVentas()).isZero();
        assertThat(reporte.getReintegros()).isZero();
        assertThat(reporte.getRecaudacionTotal()).isZero();
        assertThat(reporte.getSectores()).hasSize(1);

        verify(reporteRepository).resumirRecaudacionPorSector(
                5L,
                ESTADOS_REPORTE_ENTRADAS,
                EstadoTicket.DEVUELTO);
    }

    @Test
    void todosLosReportes_incluyenElEstadoVENDIDOEnLasConsultas() {
        when(eventoClient.obtenerEvento(5L)).thenReturn(evento(5L, "Recital", 42L,
                sector(10L, "Platea")));
        when(disponibilidadSectorRepository.findByEvento_EventoIdOrderByIdAsc(5L))
                .thenReturn(List.of(disponibilidadSector(10L, 100)));
        when(reporteRepository.resumirPorSector(
                5L, ESTADOS_VENDIDOS, EstadoTicket.DEVUELTO, ESTADOS_REPORTE_ENTRADAS))
                .thenReturn(List.of());
        when(reporteRepository.resumirRecaudacionPorSector(
                5L, ESTADOS_REPORTE_ENTRADAS, EstadoTicket.DEVUELTO))
                .thenReturn(List.of());

        service.obtenerReporteEntradasVendidas(5L, 42L, false);
        service.obtenerReporteOcupacion(5L, 42L, false);
        service.obtenerReporteRecaudacion(5L, 42L, false);

        assertThat(ESTADOS_VENDIDOS).contains(EstadoTicket.VENDIDO);
        assertThat(ESTADOS_REPORTE_ENTRADAS).contains(EstadoTicket.VENDIDO);
        verify(reporteRepository, times(2)).resumirPorSector(
                5L, ESTADOS_VENDIDOS, EstadoTicket.DEVUELTO, ESTADOS_REPORTE_ENTRADAS);
        verify(reporteRepository).resumirRecaudacionPorSector(
                5L, ESTADOS_REPORTE_ENTRADAS, EstadoTicket.DEVUELTO);
    }

    private EventoDetalleResponse evento(Long id, String nombre, Long organizadorId,
                                         SectorResponse... sectores) {
        return new EventoDetalleResponse()
                .id(id)
                .nombre(nombre)
                .fecha(FECHA_EVENTO)
                .estado(EstadoEvento.PUBLICADO)
                .organizadorId(organizadorId)
                .sectores(List.of(sectores));
    }

    private SectorResponse sector(Long id, String nombre) {
        return new SectorResponse()
                .id(id)
                .nombre(nombre);
    }

    private Object[] resumen(Long sectorId, long vendidas, long devueltas) {
        return new Object[]{sectorId, vendidas, devueltas};
    }

    private Object[] resumenRecaudacion(Long sectorId, double ventas, double reintegros) {
        return new Object[]{sectorId, ventas, reintegros};
    }

    private DisponibilidadSector disponibilidadSector(Long sectorId, Integer capacidad) {
        return DisponibilidadSector.builder()
                .sectorIdExterno(sectorId)
                .capacidadTotal(capacidad)
                .build();
    }
}
