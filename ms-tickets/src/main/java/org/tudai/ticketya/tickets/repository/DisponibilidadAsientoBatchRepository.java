package org.tudai.ticketya.tickets.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tudai.ticketya.tickets.domain.DisponibilidadAsiento;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DisponibilidadAsientoBatchRepository {

    private static final String INSERTAR_ASIENTO = """
            INSERT INTO disponibilidad_asientos (evento_id, sector_id, fila, numero, estado)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (evento_id, sector_id, fila, numero) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public int insertarLote(List<DisponibilidadAsiento> asientos, int batchSize) {
        if (asientos.isEmpty()) {
            return 0;
        }

        int tamanoLote = Math.max(1, batchSize);
        int insertados = 0;

        for (int inicio = 0; inicio < asientos.size(); inicio += tamanoLote) {
            int fin = Math.min(inicio + tamanoLote, asientos.size());
            List<DisponibilidadAsiento> lote = asientos.subList(inicio, fin);

            int[][] resultados = jdbcTemplate.batchUpdate(
                    INSERTAR_ASIENTO,
                    lote,
                    lote.size(),
                    (ps, asiento) -> {
                        ps.setLong(1, asiento.getEvento().getEventoId());
                        ps.setLong(2, asiento.getSector().getId());
                        ps.setInt(3, asiento.getFila());
                        ps.setInt(4, asiento.getNumero());
                        ps.setString(5, asiento.getEstado().name());
                    }
            );

            for (int[] resultadoLote : resultados) {
                for (int filasAfectadas : resultadoLote) {
                    insertados += filasAfectadas;
                }
            }
        }

        return insertados;
    }
}
