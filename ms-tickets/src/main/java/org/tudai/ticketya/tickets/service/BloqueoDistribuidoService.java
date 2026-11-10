package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.tickets.repository.BloqueoJobRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BloqueoDistribuidoService {

    private final BloqueoJobRepository repository;

    public Optional<Bloqueo> adquirir(String nombre, Duration duracion) {
        Instant ahora = Instant.now();
        String propietario = UUID.randomUUID().toString();
        Instant expiraEl = ahora.plus(duracion);

        try {
            if (repository.insertarSiNoExiste(nombre, propietario, ahora, expiraEl) == 1) {
                return Optional.of(new Bloqueo(nombre, propietario));
            }
        } catch (DataIntegrityViolationException e) {
            // Otra instancia gano la carrera del INSERT; se intenta tomar el lock vencido.
        }

        if (repository.tomarSiExpirado(nombre, propietario, ahora, expiraEl, ahora) == 1) {
            return Optional.of(new Bloqueo(nombre, propietario));
        }

        return Optional.empty();
    }

    public void liberar(Bloqueo bloqueo) {
        repository.liberar(bloqueo.nombre(), bloqueo.propietario());
    }

    public record Bloqueo(String nombre, String propietario) {
    }
}
