package org.tudai.ticketya.contract.notificaciones.messaging;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DetalleConfirmacionCompra {

    private Long sectorId;
    private List<AsientoNotificacion> asientos = new ArrayList<>();
    private Integer cantidad;
    private BigDecimal monto;
    private String moneda;

    public DetalleConfirmacionCompra() {
    }

    public Long getSectorId() {
        return sectorId;
    }

    public void setSectorId(Long sectorId) {
        this.sectorId = sectorId;
    }

    public List<AsientoNotificacion> getAsientos() {
        return asientos;
    }

    public void setAsientos(List<AsientoNotificacion> asientos) {
        this.asientos = asientos;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }
}
