package org.tudai.ticketya.preferencias.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.contract.preferencias.model.PreferenciaRequestDTO;
import org.tudai.ticketya.contract.preferencias.model.PreferenciaResponseDTO;
import org.tudai.ticketya.preferencias.domain.Preferencia;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PreferenciaService {

    private final PreferenciaRepository preferenciaRepository;

    /**
     * Devuelve las preferencias del cliente.
     * <p>
     * Si todavía no cargó ninguna, devuelve el mismo DTO con las listas vacías
     * en lugar de un 404. La ciudad por defecto debería salir del perfil del
     * usuario (`ms-usuarios`, US-01.01), que todavía no existe en el repo; hasta
     * entonces se devuelve {@code null}.
     *
     * @param clienteId id del cliente autenticado
     *
     * @return preferencias actuales o vacías
     */
    public PreferenciaResponseDTO obtener(Long clienteId) {
        return preferenciaRepository.findById(clienteId)
                .map(this::toResponse)
                .orElseGet(() -> respuesta(clienteId)
                        .tiposFavoritos(List.of())
                        .artistasFavoritos(List.of())
                        .ciudad(null));
    }

    /**
     * Reemplaza las preferencias del cliente.
     * <p>
     * Es un PUT de reemplazo total: el documento anterior se sobrescribe con lo
     * que venga en el request. Las listas se deduplican conservando el orden de
     * llegada y la ciudad se guarda sin espacios sobrantes.
     *
     * @param clienteId id del cliente autenticado
     * @param request   preferencias nuevas
     *
     * @return preferencias guardadas
     */
    public PreferenciaResponseDTO guardar(Long clienteId, PreferenciaRequestDTO request) {
        Preferencia preferencia = Preferencia.builder()
                .clienteId(clienteId)
                .tiposFavoritos(sinRepetidos(request.getTiposFavoritos()))
                .artistasFavoritos(sinRepetidos(request.getArtistasFavoritos()))
                .ciudad(request.getCiudad() == null ? null : request.getCiudad().trim())
                .build();

        return toResponse(preferenciaRepository.save(preferencia));
    }

    private PreferenciaResponseDTO toResponse(Preferencia preferencia) {
        return respuesta(preferencia.getClienteId())
                .tiposFavoritos(List.copyOf(preferencia.getTiposFavoritos()))
                .artistasFavoritos(List.copyOf(preferencia.getArtistasFavoritos()))
                .ciudad(preferencia.getCiudad());
    }

    private static PreferenciaResponseDTO respuesta(Long clienteId) {
        return new PreferenciaResponseDTO().clienteId(clienteId);
    }

    private static <T> List<T> sinRepetidos(List<T> valores) {
        if (valores == null || valores.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(valores));
    }
}
