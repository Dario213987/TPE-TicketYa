package org.tudai.ticketya.devoluciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tudai.ticketya.contract.devoluciones.model.EstadoDevolucion;
import org.tudai.ticketya.persistence.Auditable;

import java.time.Instant;

@Entity
@Table(name = "devoluciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Devolucion extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Long clienteId;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    @Column(name = "evento_id", updatable = false)
    private Long eventoId;

    @Column(name = "fecha", nullable = false, updatable = false)
    private Instant fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoDevolucion estado;

    @Column(name = "transaccion_id")
    private Long transaccionId;

    @Column(name = "motivo", length = 255)
    private String motivo;

    @Override
    protected void onCreate() {
        super.onCreate();
        if (this.fecha == null) {
            this.fecha = getCreadoEl();
        }
    }
}
