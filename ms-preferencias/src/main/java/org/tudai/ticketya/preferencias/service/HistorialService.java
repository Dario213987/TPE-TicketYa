package org.tudai.ticketya.preferencias.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.contract.preferencias.model.HistorialCompraDTO;
import org.tudai.ticketya.preferencias.domain.HistorialCompra;
import org.tudai.ticketya.preferencias.domain.Preferencia;
import org.tudai.ticketya.preferencias.event.EventoTicketUtilizado;
import org.tudai.ticketya.preferencias.event.EventoVentaPagada;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Historial de compras y asistencias del cliente (US-09.02).
 * <p>
 * Lo alimentan únicamente los eventos asíncronos {@code venta.pagada} y
 * {@code ticket.utilizado}. La escritura es idempotente por evento: una compra
 * del mismo evento actualiza la entrada existente en lugar de duplicarla, y un
 * uso solo prende {@code usado} para que la recomendación lo pondere más.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistorialService {

    private final PreferenciaRepository preferenciaRepository;
    private final CoCompraService coCompraService;
    private final ModelMapper modelMapper;

    /**
     * Registra una compra confirmada. Si el evento ya estaba en el historial
     * (por ejemplo, por un reintento del mismo mensaje), completa los datos sin
     * duplicar la entrada y conserva el flag de uso.
     * <p>
     * Cuando la compra es nueva para el cliente, además actualiza la
     * co-ocurrencia con los eventos que ya había comprado (US-09.03). Si la
     * actualización de co-ocurrencia falla, se registra y no se propaga: el
     * historial ya quedó guardado y el mensaje se confirma igual.
     *
     * @param evento datos publicados en {@code venta.pagada}
     */
    public void registrarCompra(EventoVentaPagada evento) {
        Preferencia preferencia = buscarOCrear(evento.clienteId());
        boolean compraNueva = !yaComprado(preferencia, evento.eventoId());
        List<Long> eventosPrevios = eventosComprados(preferencia, evento.eventoId());

        HistorialCompra entrada = entradaDe(preferencia, evento.eventoId());

        entrada.setComprado(true);
        if (evento.tipo() != null) {
            entrada.setTipo(evento.tipo());
        }
        if (evento.participanteIds() != null && !evento.participanteIds().isEmpty()) {
            entrada.setParticipanteIds(evento.participanteIds());
        }
        if (evento.ciudad() != null && !evento.ciudad().isBlank()) {
            entrada.setCiudad(evento.ciudad());
        }
        if (evento.fechaEvento() != null) {
            entrada.setFecha(evento.fechaEvento());
        }

        preferenciaRepository.save(preferencia);

        if (compraNueva && !eventosPrevios.isEmpty()) {
            try {
                coCompraService.registrarCompra(evento.eventoId(), eventosPrevios);
            } catch (RuntimeException e) {
                log.warn("No se pudo actualizar la co-ocurrencia del evento {}: {}",
                        evento.eventoId(), e.getMessage());
            }
        }
    }

    /**
     * Marca como usado el evento del historial. Si la entrada todavía no existe
     * (por ejemplo, el uso llegó antes que la compra), la crea para no perder el
     * dato; la compra posterior completa tipo, participantes, ciudad y fecha.
     *
     * @param evento datos publicados en {@code ticket.utilizado}
     */
    public void registrarUso(EventoTicketUtilizado evento) {
        Preferencia preferencia = buscarOCrear(evento.clienteId());
        entradaDe(preferencia, evento.eventoId()).setUsado(true);
        preferenciaRepository.save(preferencia);
    }

    /**
     * Devuelve el historial del cliente. Si todavía no compró nada, devuelve una
     * lista vacía.
     *
     * @param clienteId id del cliente autenticado
     *
     * @return historial en orden de inserción
     */
    public List<HistorialCompraDTO> obtenerHistorial(Long clienteId) {
        return preferenciaRepository.findById(clienteId)
                .map(Preferencia::getHistorial)
                .orElseGet(List::of)
                .stream()
                .map(entrada -> modelMapper.map(entrada, HistorialCompraDTO.class))
                .toList();
    }

    private Preferencia buscarOCrear(Long clienteId) {
        return preferenciaRepository.findById(clienteId)
                .orElseGet(() -> Preferencia.builder().clienteId(clienteId).build());
    }

    private boolean yaComprado(Preferencia preferencia, Long eventoId) {
        return historialDe(preferencia).stream()
                .anyMatch(entrada -> Objects.equals(entrada.getEventoId(), eventoId)
                        && entrada.isComprado());
    }

    private List<Long> eventosComprados(Preferencia preferencia, Long eventoId) {
        return historialDe(preferencia).stream()
                .filter(HistorialCompra::isComprado)
                .filter(entrada -> !Objects.equals(entrada.getEventoId(), eventoId))
                .map(HistorialCompra::getEventoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static List<HistorialCompra> historialDe(Preferencia preferencia) {
        return preferencia.getHistorial() == null ? List.of() : preferencia.getHistorial();
    }

    private HistorialCompra entradaDe(Preferencia preferencia, Long eventoId) {
        List<HistorialCompra> historial = preferencia.getHistorial() == null
                ? new ArrayList<>()
                : new ArrayList<>(preferencia.getHistorial());
        preferencia.setHistorial(historial);

        return historial.stream()
                .filter(entrada -> Objects.equals(entrada.getEventoId(), eventoId))
                .findFirst()
                .orElseGet(() -> {
                    HistorialCompra nueva = HistorialCompra.builder().eventoId(eventoId).build();
                    historial.add(nueva);
                    return nueva;
                });
    }
}
