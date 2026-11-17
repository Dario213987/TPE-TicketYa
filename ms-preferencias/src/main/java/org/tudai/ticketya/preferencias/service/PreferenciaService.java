package org.tudai.ticketya.preferencias.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
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
    private final ModelMapper modelMapper;

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
                .orElseGet(() -> toResponse(Preferencia.builder().clienteId(clienteId).build()));
    }

    /**
     * Reemplaza las preferencias del cliente.
     * <p>
     * Es un PUT de reemplazo total de los gustos declarados: el documento anterior
     * se sobrescribe con lo que venga en el request. Las listas se deduplican
     * conservando el orden de llegada y la ciudad se guarda sin espacios sobrantes.
     * El historial de compras y asistencias no se toca: lo mantiene el consumidor
     * de eventos.
     *
     * @param clienteId id del cliente autenticado
     * @param request   preferencias nuevas
     *
     * @return preferencias guardadas
     */
    public PreferenciaResponseDTO guardar(Long clienteId, PreferenciaRequestDTO request) {
        Preferencia preferencia = preferenciaRepository.findById(clienteId)
                .orElseGet(() -> Preferencia.builder().clienteId(clienteId).build());
        Preferencia datos = modelMapper.map(request, Preferencia.class);

        preferencia.setClienteId(clienteId);
        preferencia.setTiposFavoritos(sinRepetidos(datos.getTiposFavoritos()));
        preferencia.setArtistasFavoritos(sinRepetidos(datos.getArtistasFavoritos()));
        preferencia.setCiudad(datos.getCiudad() == null ? null : datos.getCiudad().trim());

        return toResponse(preferenciaRepository.save(preferencia));
    }

    private PreferenciaResponseDTO toResponse(Preferencia preferencia) {
        return modelMapper.map(preferencia, PreferenciaResponseDTO.class);
    }

    private static <T> List<T> sinRepetidos(List<T> valores) {
        if (valores == null || valores.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(valores));
    }
}
