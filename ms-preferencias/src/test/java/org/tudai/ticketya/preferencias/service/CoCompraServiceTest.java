package org.tudai.ticketya.preferencias.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tudai.ticketya.preferencias.domain.CoCompra;
import org.tudai.ticketya.preferencias.domain.Relacionado;
import org.tudai.ticketya.preferencias.repository.CoCompraRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoCompraServiceTest {

    @Mock
    private CoCompraRepository coCompraRepository;

    private Map<Long, CoCompra> documentos;
    private CoCompraService coCompraService;

    @BeforeEach
    void setUp() {
        documentos = new HashMap<>();
        coCompraService = new CoCompraService(coCompraRepository);

        given(coCompraRepository.findById(anyLong()))
                .willAnswer(invocacion -> Optional.ofNullable(
                        documentos.get(invocacion.getArgument(0))));
        given(coCompraRepository.save(any(CoCompra.class)))
                .willAnswer(invocacion -> {
                    CoCompra guardado = invocacion.getArgument(0);
                    documentos.put(guardado.getEventoId(), guardado);
                    return guardado;
                });
        given(coCompraRepository.findAllById(any()))
                .willAnswer(invocacion -> {
                    List<CoCompra> encontrados = new ArrayList<>();
                    for (Long id : invocacion.<Iterable<Long>>getArgument(0)) {
                        if (documentos.containsKey(id)) {
                            encontrados.add(documentos.get(id));
                        }
                    }
                    return encontrados;
                });
    }

    @Test
    void registrarCompra_sinPrevios_noGuardaNada() {
        coCompraService.registrarCompra(5L, List.of());

        assertThat(documentos).isEmpty();
    }

    @Test
    void registrarCompra_conUnPrevio_creaAmbasDirecciones() {
        coCompraService.registrarCompra(6L, List.of(5L));

        assertThat(cantidad(6L, 5L)).isEqualTo(1);
        assertThat(cantidad(5L, 6L)).isEqualTo(1);
    }

    @Test
    void registrarCompra_conVariosPrevios_agregaTodosLosPares() {
        coCompraService.registrarCompra(7L, List.of(5L, 6L));

        assertThat(cantidad(7L, 5L)).isEqualTo(1);
        assertThat(cantidad(7L, 6L)).isEqualTo(1);
        assertThat(cantidad(5L, 7L)).isEqualTo(1);
        assertThat(cantidad(6L, 7L)).isEqualTo(1);
    }

    @Test
    void registrarCompra_siElEventoEstaEnLosPrevios_seIgnora() {
        coCompraService.registrarCompra(5L, List.of(5L));

        assertThat(documentos).isEmpty();
    }

    @Test
    void registrarCompra_otroClienteConLosMismosEventos_incrementaLaCantidad() {
        coCompraService.registrarCompra(6L, List.of(5L));
        coCompraService.registrarCompra(6L, List.of(5L));

        assertThat(cantidad(6L, 5L)).isEqualTo(2);
        assertThat(cantidad(5L, 6L)).isEqualTo(2);
    }

    @Test
    void coOcurrencias_agregaLasCantidadesDeLosEventosBase() {
        coCompraService.registrarCompra(6L, List.of(5L));
        coCompraService.registrarCompra(7L, List.of(5L));

        Map<Long, Long> totales = coCompraService.coOcurrencias(List.of(6L, 7L));

        assertThat(totales).containsEntry(5L, 2L);
    }

    @Test
    void coOcurrencias_sinEventosBase_devuelveVacio() {
        assertThat(coCompraService.coOcurrencias(List.of())).isEmpty();
    }

    @Test
    void coOcurrencias_sinDocumentos_devuelveVacio() {
        assertThat(coCompraService.coOcurrencias(List.of(99L))).isEmpty();
    }

    private long cantidad(Long eventoId, Long relacionadoId) {
        CoCompra documento = documentos.get(eventoId);
        assertThat(documento).isNotNull();
        return documento.getRelacionados().stream()
                .filter(relacionado -> relacionado.getEventoId().equals(relacionadoId))
                .map(Relacionado::getCantidad)
                .findFirst()
                .orElse(0L);
    }
}
