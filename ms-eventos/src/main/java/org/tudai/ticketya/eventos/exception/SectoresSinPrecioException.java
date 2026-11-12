package org.tudai.ticketya.eventos.exception;

import java.util.List;
import java.util.stream.Collectors;

public class SectoresSinPrecioException extends RuntimeException {

    public record SectorFaltante(Long id, String nombre) {}

    private final List<SectorFaltante> sectores;

    public SectoresSinPrecioException(List<SectorFaltante> sectores) {
        super("Hay sectores sin precio");
        this.sectores = List.copyOf(sectores);
    }

    public List<SectorFaltante> getSectores() {
        return sectores;
    }

    public String nombres() {
        return sectores.stream()
                .map(SectorFaltante::nombre)
                .collect(Collectors.joining(", "));
    }
}
