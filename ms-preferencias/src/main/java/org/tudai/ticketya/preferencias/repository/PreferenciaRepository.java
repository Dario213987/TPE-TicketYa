package org.tudai.ticketya.preferencias.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.tudai.ticketya.preferencias.domain.Preferencia;

public interface PreferenciaRepository extends MongoRepository<Preferencia, Long> {
}
