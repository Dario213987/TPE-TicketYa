package org.tudai.ticketya.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.tudai.ticketya.eventos.domain.Participante;

import java.util.List;

public interface ParticipanteRepository extends JpaRepository<Participante, Long> {

    @Query("select p.id as id, p.nombre as nombre from Participante p")
    List<ParticipanteIdNombre> findAllIdAndNombre();

    interface ParticipanteIdNombre {
        Long getId();

        String getNombre();
    }
}
