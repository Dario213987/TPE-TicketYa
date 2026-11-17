package org.tudai.ticketya.preferencias.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.contract.preferencias.model.HistorialCompraDTO;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;
import org.tudai.ticketya.preferencias.config.ModelMapperConfig;
import org.tudai.ticketya.preferencias.domain.HistorialCompra;
import org.tudai.ticketya.preferencias.domain.Preferencia;
import org.tudai.ticketya.preferencias.event.EventoTicketUtilizado;
import org.tudai.ticketya.preferencias.event.EventoVentaPagada;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HistorialServiceTest {

    private static final Instant FECHA_EVENTO = Instant.parse("2026-12-01T20:00:00Z");
    private static final LocalDateTime FECHA_HISTORIAL = LocalDateTime.parse("2026-12-01T20:00:00");

    @Mock
    private PreferenciaRepository preferenciaRepository;

    @Mock
    private CoCompraService coCompraService;

    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @InjectMocks
    private HistorialService historialService;

    @Test
    void registrarCompra_sinDocumento_creaElHistorialYNoTocaLasPreferencias() {
        given(preferenciaRepository.findById(7L)).willReturn(Optional.empty());
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        historialService.registrarCompra(compra(7L, 5L, TipoEvento.RECITAL, List.of(15L, 20L), "Tandil"));

        Preferencia guardada = capturar();
        assertThat(guardada.getClienteId()).isEqualTo(7L);
        assertThat(guardada.getTiposFavoritos()).isEmpty();
        assertThat(guardada.getArtistasFavoritos()).isEmpty();
        assertThat(guardada.getHistorial()).singleElement().satisfies(entrada -> {
            assertThat(entrada.getEventoId()).isEqualTo(5L);
            assertThat(entrada.getTipo()).isEqualTo(TipoEvento.RECITAL);
            assertThat(entrada.getParticipanteIds()).containsExactly(15L, 20L);
            assertThat(entrada.getCiudad()).isEqualTo("Tandil");
            assertThat(entrada.getFecha()).isEqualTo(FECHA_EVENTO);
            assertThat(entrada.isComprado()).isTrue();
            assertThat(entrada.isUsado()).isFalse();
        });
    }

    @Test
    void registrarCompra_eventoRepetido_noDuplicaLaEntrada() {
        Preferencia[] documento = {null};
        given(preferenciaRepository.findById(7L))
                .willAnswer(invocacion -> Optional.ofNullable(documento[0]));
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> {
                    documento[0] = invocacion.getArgument(0);
                    return documento[0];
                });

        EventoVentaPagada compra = compra(7L, 5L, TipoEvento.RECITAL, List.of(15L), "Tandil");
        historialService.registrarCompra(compra);
        historialService.registrarCompra(compra);

        assertThat(documento[0].getHistorial()).hasSize(1);
        assertThat(documento[0].getHistorial().get(0).getEventoId()).isEqualTo(5L);
    }

    @Test
    void registrarCompra_preservaLasPreferenciasYLosOtrosEventos() {
        Preferencia existente = Preferencia.builder()
                .clienteId(7L)
                .tiposFavoritos(List.of(TipoEvento.TEATRO))
                .artistasFavoritos(List.of(20L))
                .ciudad("Tandil")
                .historial(List.of(HistorialCompra.builder()
                        .eventoId(4L)
                        .tipo(TipoEvento.FESTIVAL)
                        .comprado(true)
                        .build()))
                .build();
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(existente));
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        historialService.registrarCompra(compra(7L, 5L, TipoEvento.RECITAL, List.of(15L), "Tandil"));

        Preferencia guardada = capturar();
        assertThat(guardada.getTiposFavoritos()).containsExactly(TipoEvento.TEATRO);
        assertThat(guardada.getArtistasFavoritos()).containsExactly(20L);
        assertThat(guardada.getCiudad()).isEqualTo("Tandil");
        assertThat(guardada.getHistorial())
                .extracting(HistorialCompra::getEventoId)
                .containsExactly(4L, 5L);
    }

    @Test
    void registrarUso_marcaElEventoComoUsado() {
        Preferencia existente = Preferencia.builder()
                .clienteId(7L)
                .historial(List.of(HistorialCompra.builder()
                        .eventoId(5L)
                        .tipo(TipoEvento.RECITAL)
                        .comprado(true)
                        .build()))
                .build();
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(existente));
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        historialService.registrarUso(new EventoTicketUtilizado(
                7L, 5L, 99L, Instant.parse("2026-12-02T21:00:00Z"), "trace-2"));

        Preferencia guardada = capturar();
        assertThat(guardada.getHistorial()).singleElement().satisfies(entrada -> {
            assertThat(entrada.getEventoId()).isEqualTo(5L);
            assertThat(entrada.isComprado()).isTrue();
            assertThat(entrada.isUsado()).isTrue();
        });
    }

    @Test
    void registrarUso_sinCompraPrevia_creaLaEntradaParaNoPerderElDato() {
        given(preferenciaRepository.findById(7L)).willReturn(Optional.empty());
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        historialService.registrarUso(new EventoTicketUtilizado(
                7L, 5L, 99L, Instant.parse("2026-12-02T21:00:00Z"), "trace-2"));

        Preferencia guardada = capturar();
        assertThat(guardada.getHistorial()).singleElement().satisfies(entrada -> {
            assertThat(entrada.getEventoId()).isEqualTo(5L);
            assertThat(entrada.isComprado()).isFalse();
            assertThat(entrada.isUsado()).isTrue();
        });
    }

    @Test
    void registrarCompra_despuesDelUso_preservaElFlagYCompletaLosDatos() {
        Preferencia existente = Preferencia.builder()
                .clienteId(7L)
                .historial(List.of(HistorialCompra.builder()
                        .eventoId(5L)
                        .usado(true)
                        .build()))
                .build();
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(existente));
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        historialService.registrarCompra(compra(7L, 5L, TipoEvento.RECITAL, List.of(15L), "Tandil"));

        Preferencia guardada = capturar();
        assertThat(guardada.getHistorial()).singleElement().satisfies(entrada -> {
            assertThat(entrada.isComprado()).isTrue();
            assertThat(entrada.isUsado()).isTrue();
            assertThat(entrada.getTipo()).isEqualTo(TipoEvento.RECITAL);
            assertThat(entrada.getCiudad()).isEqualTo("Tandil");
            assertThat(entrada.getFecha()).isEqualTo(FECHA_EVENTO);
        });
    }

    @Test
    void obtenerHistorial_sinDocumento_devuelveListaVacia() {
        given(preferenciaRepository.findById(7L)).willReturn(Optional.empty());

        assertThat(historialService.obtenerHistorial(7L)).isEmpty();
    }

    @Test
    void obtenerHistorial_mapeaElHistorialDelCliente() {
        Preferencia existente = Preferencia.builder()
                .clienteId(7L)
                .historial(List.of(HistorialCompra.builder()
                        .eventoId(5L)
                        .tipo(TipoEvento.RECITAL)
                        .participanteIds(List.of(15L))
                        .ciudad("Tandil")
                        .fecha(FECHA_EVENTO)
                        .comprado(true)
                        .usado(true)
                        .build()))
                .build();
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(existente));

        List<HistorialCompraDTO> historial = historialService.obtenerHistorial(7L);

        assertThat(historial).singleElement().satisfies(entrada -> {
            assertThat(entrada.getEventoId()).isEqualTo(5L);
            assertThat(entrada.getTipo()).isEqualTo(TipoEvento.RECITAL);
            assertThat(entrada.getParticipanteIds()).containsExactly(15L);
            assertThat(entrada.getCiudad()).isEqualTo("Tandil");
            assertThat(entrada.getFecha()).isEqualTo(FECHA_HISTORIAL);
            assertThat(entrada.getComprado()).isTrue();
            assertThat(entrada.getUsado()).isTrue();
        });
    }

    @Test
    void registrarCompra_eventoNuevoConPrevios_actualizaLaCoCompra() {
        Preferencia existente = Preferencia.builder()
                .clienteId(7L)
                .historial(List.of(HistorialCompra.builder()
                        .eventoId(4L)
                        .comprado(true)
                        .build()))
                .build();
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(existente));
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        historialService.registrarCompra(compra(7L, 5L, TipoEvento.RECITAL, List.of(15L), "Tandil"));

        verify(coCompraService).registrarCompra(5L, List.of(4L));
    }

    @Test
    void registrarCompra_eventoYaComprado_noActualizaLaCoCompra() {
        Preferencia existente = Preferencia.builder()
                .clienteId(7L)
                .historial(List.of(HistorialCompra.builder()
                        .eventoId(5L)
                        .comprado(true)
                        .build()))
                .build();
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(existente));
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        historialService.registrarCompra(compra(7L, 5L, TipoEvento.RECITAL, List.of(15L), "Tandil"));

        verifyNoInteractions(coCompraService);
    }

    @Test
    void registrarCompra_sinPrevios_noActualizaLaCoCompra() {
        given(preferenciaRepository.findById(7L)).willReturn(Optional.empty());
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));

        historialService.registrarCompra(compra(7L, 5L, TipoEvento.RECITAL, List.of(15L), "Tandil"));

        verifyNoInteractions(coCompraService);
    }

    @Test
    void registrarCompra_siFallaLaCoCompra_noPropagaYGuardaElHistorial() {
        Preferencia existente = Preferencia.builder()
                .clienteId(7L)
                .historial(List.of(HistorialCompra.builder()
                        .eventoId(4L)
                        .comprado(true)
                        .build()))
                .build();
        given(preferenciaRepository.findById(7L)).willReturn(Optional.of(existente));
        given(preferenciaRepository.save(any(Preferencia.class)))
                .willAnswer(invocacion -> invocacion.getArgument(0));
        doThrow(new RuntimeException("boom"))
                .when(coCompraService).registrarCompra(any(), any());

        historialService.registrarCompra(compra(7L, 5L, TipoEvento.RECITAL, List.of(15L), "Tandil"));

        verify(coCompraService).registrarCompra(5L, List.of(4L));
        verify(preferenciaRepository).save(any(Preferencia.class));
        assertThat(existente.getHistorial())
                .extracting(HistorialCompra::getEventoId)
                .contains(4L, 5L);
    }

    private Preferencia capturar() {
        ArgumentCaptor<Preferencia> captor = ArgumentCaptor.forClass(Preferencia.class);
        verify(preferenciaRepository).save(captor.capture());
        return captor.getValue();
    }

    private static EventoVentaPagada compra(Long clienteId, Long eventoId, TipoEvento tipo,
                                            List<Long> participantes, String ciudad) {
        return new EventoVentaPagada(clienteId, eventoId, tipo, participantes, ciudad,
                FECHA_EVENTO, "trace-1");
    }
}
