package org.tudai.ticketya.preferencias.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.preferencias.domain.Preferencia;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;
import org.tudai.ticketya.preferencias.util.NormalizadorTexto;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DestinatariosService {

    private final PreferenciaRepository preferenciaRepository;

    public List<Long> obtenerDestinatarios(List<Long> participanteIds, String ciudad) {
        if (participanteIds == null || participanteIds.isEmpty()) {
            return List.of();
        }

        Set<Long> ids = participanteIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }

        String ciudadNormalizada = NormalizadorTexto.normalizar(ciudad);
        if (ciudadNormalizada == null || ciudadNormalizada.isEmpty()) {
            return List.of();
        }

        return preferenciaRepository.findByArtistasFavoritosIn(ids).stream()
                .filter(preferencia -> preferencia.getClienteId() != null)
                .filter(preferencia -> preferencia.getCiudad() != null)
                .filter(preferencia -> ciudadNormalizada.equals(
                        NormalizadorTexto.normalizar(preferencia.getCiudad())))
                .filter(preferencia -> preferencia.getArtistasFavoritos() != null)
                .filter(preferencia -> preferencia.getArtistasFavoritos().stream()
                        .anyMatch(ids::contains))
                .map(Preferencia::getClienteId)
                .distinct()
                .sorted()
                .toList();
    }
}
