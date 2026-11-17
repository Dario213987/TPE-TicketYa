package org.tudai.ticketya.preferencias.service;

import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class HistorialService {

    private final PreferenciaRepository preferenciaRepository;
    private final ModelMapper modelMapper;

    /**
     * Registra una compra confirmada. Si el evento ya estaba en el historial
     * (por ejemplo, por un reintento del mismo mensaje), completa los datos sin
     * duplicar la entrada y conserva el flag de uso.
     *
     * @param evento datos publicados en {@code venta.pagada}
     */
    public void registrarCompra(EventoVentaPagada evento) {
        Preferencia preferencia = buscarOCrear(evento.clienteId());
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
