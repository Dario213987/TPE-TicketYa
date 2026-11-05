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

@Entity
@Table(name = "bloqueos_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloqueoJob {

    @Id
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "propietario", nullable = false, length = 100)
    private String propietario;

    @Column(name = "adquirido_el", nullable = false)
    private Instant adquiridoEl;

    @Column(name = "expira_el", nullable = false)
    private Instant expiraEl;
}
