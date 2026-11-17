package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Ítem de una venta: un ticket con su sector, asiento y el precio cobrado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "venta_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_venta_items_ticket", columnNames = "ticket_id")
)
public class VentaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "sector_id_externo", nullable = false, updatable = false)
    private Long sectorIdExterno;

    @Column(name = "fila", updatable = false)
    private Integer fila;

    @Column(name = "numero", updatable = false)
    private Integer numero;

    @Column(name = "precio", nullable = false, updatable = false)
    private Double precio;

    @Column(name = "transaccion_id", updatable = false)
    private Long transaccionId;
}
