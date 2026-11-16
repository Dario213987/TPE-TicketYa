package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Configuración y estado de la habilitación por tandas de un evento de alta demanda.
 * <p>
 * {@code proximaTandaEl} es el punto de coordinación entre instancias: el job lo
 * actualiza de forma atómica antes de emitir una tanda, de modo que solo una
 * instancia habilita a los siguientes clientes en cada intervalo.
 */
@Entity
@Table(name = "habilitacion_eventos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabilitacionEvento {

    @Id
    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;

    @Builder.Default
    @Column(name = "habilitada", nullable = false)
    private boolean habilitada = true;

    @Column(name = "tamano_tanda", nullable = false)
    private int tamanoTanda;

    @Column(name = "intervalo_segundos", nullable = false)
    private int intervaloSegundos;

    @Column(name = "ttl_pase_segundos", nullable = false)
    private int ttlPaseSegundos;

    @Column(name = "proxima_tanda_el", nullable = false)
    private Instant proximaTandaEl;

    @Column(name = "ultima_tanda_el")
    private Instant ultimaTandaEl;

    @Builder.Default
    @Column(name = "total_habilitados", nullable = false)
    private long totalHabilitados = 0;
}
