package org.tudai.ticketya.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.Instant;

@MappedSuperclass
@Getter
public abstract class Auditable {

    @Column(name = "creado_el", nullable = false, updatable = false)
    private Instant creadoEl;

    @Column(name = "actualizado_el", nullable = false)
    private Instant actualizadoEl;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.creadoEl = now;
        this.actualizadoEl = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEl = Instant.now();
    }
}
