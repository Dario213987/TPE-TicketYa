package org.tudai.ticketya.preferencias.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.tudai.ticketya.preferencias.domain.CoCompra;

public interface CoCompraRepository extends MongoRepository<CoCompra, Long> {
}
