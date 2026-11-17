package org.tudai.ticketya.preferencias.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.tudai.ticketya.preferencias.domain.Preferencia;

import java.util.Collection;
import java.util.List;

public interface PreferenciaRepository extends MongoRepository<Preferencia, Long> {

    List<Preferencia> findByArtistasFavoritosIn(Collection<Long> participanteIds);
}
