package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro de una compra confirmada, para auditoría y reportes. Los precios de los ítems
 * son los vigentes al momento de la compra.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "ventas",
        uniqueConstraints = @UniqueConstraint(name = "uq_ventas_reserva", columnNames = "reserva_id"),
        indexes = @Index(name = "idx_ventas_evento_cliente", columnList = "evento_id, cliente_id")
)
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false, updatable = false)
    private Long reservaId;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Long clienteId;

    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;

    @Column(name = "total", nullable = false, updatable = false)
    private Double total;

    @Column(name = "medio_pago", nullable = false, length = 30, updatable = false)
    private String medioPago;

    @Column(name = "creado_el", nullable = false, updatable = false)
    private Instant creadoEl;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<VentaItem> items = new ArrayList<>();

    public void agregarItem(VentaItem item) {
        item.setVenta(this);
        items.add(item);
    }
}
