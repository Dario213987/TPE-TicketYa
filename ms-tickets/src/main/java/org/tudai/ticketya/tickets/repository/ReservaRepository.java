package org.tudai.ticketya.tickets.repository;

import java.util.List;

import java.time.Instant;

import org.tudai.ticketya.tickets.domain.EstadoPago;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tudai.ticketya.tickets.domain.Reserva;

import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.id = :id")
    Optional<Reserva> findByIdForUpdate(@Param("id") Long id);

    /** Si el cliente ya usó esa Idempotency-Key para pagar otra reserva (TKY-88). */
    boolean existsByClienteIdAndIdempotencyKeyAndIdNot(Long clienteId, String idempotencyKey, Long id);

    /** Reservas con el pago sin resolver que vencieron antes de {@code limite} (TKY-87). */
    @Query("""
            select r.id from Reserva r
            where r.estadoPago = :estado and r.expiraEl < :limite
            order by r.id
            """)
    List<Long> findIdsByEstadoPagoAndExpiraElBefore(@Param("estado") EstadoPago estado,
                                                     @Param("limite") Instant limite);
}
