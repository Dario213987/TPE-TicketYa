package org.tudai.ticketya.tickets.migration;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiquibaseMigrationTest {

    private Connection connection;
    private Liquibase liquibase;

    @BeforeEach
    void migrar() throws Exception {
        String url = "jdbc:h2:mem:tickets-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        connection = DriverManager.getConnection(url, "sa", "");
        liquibase = new Liquibase(
                "db/changelog/db.changelog-master.yaml",
                new ClassLoaderResourceAccessor(),
                new JdbcConnection(connection));
        liquibase.update(new Contexts());
    }

    @AfterEach
    void cerrar() throws Exception {
        connection.close();
    }

    @Test
    void creaLasTablasDelServicio() throws Exception {
        assertThat(tablas())
                .contains("disponibilidad_eventos",
                        "disponibilidad_sectores",
                        "disponibilidad_asientos",
                        "tickets",
                        "reservas",
                        "bloqueos_job",
                        "ventas",
                        "venta_items");
    }

    @Test
    void creaLasColumnasEsperadasEnCadaTabla() throws Exception {
        assertThat(columnas("disponibilidad_eventos"))
                .containsExactlyInAnyOrder("evento_id", "creado_el", "limite_entradas_por_cliente",
                        "organizador_id");

        assertThat(columnas("disponibilidad_sectores"))
                .containsExactlyInAnyOrder("id", "evento_id", "sector_id_externo", "tipo",
                        "capacidad_total", "disponibles", "precio");

        assertThat(columnas("disponibilidad_asientos"))
                .containsExactlyInAnyOrder("id", "evento_id", "sector_id", "fila", "numero",
                        "estado");

        assertThat(columnas("tickets"))
                .containsExactlyInAnyOrder("id", "codigo_qr", "estado_ticket", "precio",
                        "fecha_emision", "cliente_id", "expira_el", "evento_id", "sector_id",
                        "asiento_id", "reserva_id", "venta_id", "pago_en_curso");

        assertThat(columnas("ventas"))
                .containsExactlyInAnyOrder("id", "reserva_id", "cliente_id", "evento_id", "total",
                        "medio_pago", "creado_el");

        assertThat(columnas("venta_items"))
                .containsExactlyInAnyOrder("id", "venta_id", "ticket_id", "sector_id_externo", "fila",
                        "numero", "precio", "transaccion_id");

        assertThat(columnas("reservas"))
                .containsExactlyInAnyOrder("id", "evento_id", "cliente_id", "sector_id_externo",
                        "expira_el", "creado_el", "idempotency_key", "medio_pago", "estado_pago",
                        "motivo_rechazo", "pagado_el", "pago_en_curso");
    }

    @Test
    void creaLosIndicesYRestriccionesEsperados() throws Exception {
        assertThat(indices("disponibilidad_sectores")).contains("idx_disponibilidad_sector_evento");
        assertThat(indices("disponibilidad_asientos"))
                .contains("idx_disponibilidad_asiento_evento_estado",
                        "idx_disponibilidad_asiento_sector_estado");
        assertThat(indices("tickets"))
                .contains("idx_tickets_evento_cliente_estado", "idx_tickets_estado_expira");
        assertThat(indices("reservas")).contains("idx_reservas_evento_cliente");

        assertThat(restricciones("disponibilidad_sectores"))
                .contains("pk_disponibilidad_sectores",
                        "uq_disponibilidad_sector",
                        "fk_disponibilidad_sectores_evento");
        assertThat(restricciones("disponibilidad_asientos"))
                .contains("pk_disponibilidad_asientos",
                        "uq_disponibilidad_asiento",
                        "fk_disponibilidad_asientos_evento",
                        "fk_disponibilidad_asientos_sector");
        assertThat(restricciones("tickets"))
                .contains("pk_tickets",
                        "uq_tickets_codigo_qr",
                        "uq_tickets_asiento",
                        "fk_tickets_evento",
                        "fk_tickets_sector",
                        "fk_tickets_asiento",
                        "fk_tickets_reserva");
        assertThat(restricciones("reservas"))
                .contains("pk_reservas", "fk_reservas_evento");
    }

    @Test
    void laMigracionEsIdempotente() throws Exception {
        liquibase.update(new Contexts());

        assertThat(tablas()).contains("disponibilidad_eventos", "tickets", "reservas",
                "bloqueos_job");
        assertThat(contarCambiosAplicados()).isEqualTo(8);
    }

    @Test
    void aplicaLasRestriccionesDeUnicidadYClavesForaneas() throws Exception {
        ejecutar("""
                insert into disponibilidad_eventos
                    (evento_id, creado_el, limite_entradas_por_cliente)
                values (1, current_timestamp, 4)
                """);
        ejecutar("""
                insert into disponibilidad_sectores
                    (evento_id, sector_id_externo, tipo, capacidad_total, disponibles)
                values (1, 10, 'NO_NUMERADO', 100, 100)
                """);

        assertThatThrownBy(() -> ejecutar("""
                insert into disponibilidad_sectores
                    (evento_id, sector_id_externo, tipo, capacidad_total, disponibles)
                values (1, 10, 'NO_NUMERADO', 100, 100)
                """)).isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> ejecutar("""
                insert into disponibilidad_sectores
                    (evento_id, sector_id_externo, tipo, capacidad_total, disponibles)
                values (999, 20, 'NO_NUMERADO', 100, 100)
                """)).isInstanceOf(SQLException.class);

        long sectorId = primerLong("select id from disponibilidad_sectores where sector_id_externo = 10");
        ejecutar("""
                insert into disponibilidad_asientos
                    (evento_id, sector_id, fila, numero, estado)
                values (1, %d, 1, 1, 'LIBRE')
                """.formatted(sectorId));
        long asientoId = primerLong("select id from disponibilidad_asientos");

        ejecutar("""
                insert into tickets
                    (codigo_qr, estado_ticket, fecha_emision, evento_id, sector_id, asiento_id)
                values ('qr-1', 'EMITIDO', current_timestamp, 1, %d, %d)
                """.formatted(sectorId, asientoId));

        assertThatThrownBy(() -> ejecutar("""
                insert into tickets
                    (codigo_qr, estado_ticket, fecha_emision, evento_id, sector_id, asiento_id)
                values ('qr-1', 'EMITIDO', current_timestamp, 1, %d, null)
                """.formatted(sectorId))).isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> ejecutar("""
                insert into tickets
                    (codigo_qr, estado_ticket, fecha_emision, evento_id, sector_id, asiento_id)
                values ('qr-2', 'EMITIDO', current_timestamp, 1, %d, %d)
                """.formatted(sectorId, asientoId))).isInstanceOf(SQLException.class);
    }

    private Set<String> tablas() throws SQLException {
        Set<String> resultado = new HashSet<>();
        try (ResultSet rs = connection.getMetaData()
                .getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                resultado.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return resultado;
    }

    private Set<String> columnas(String tabla) throws SQLException {
        Set<String> resultado = new HashSet<>();
        try (ResultSet rs = connection.getMetaData()
                .getColumns(null, null, tabla.toUpperCase(Locale.ROOT), "%")) {
            while (rs.next()) {
                resultado.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return resultado;
    }

    private Set<String> indices(String tabla) throws SQLException {
        Set<String> resultado = new HashSet<>();
        try (ResultSet rs = connection.getMetaData()
                .getIndexInfo(null, null, tabla.toUpperCase(Locale.ROOT), false, false)) {
            while (rs.next()) {
                String nombre = rs.getString("INDEX_NAME");
                if (nombre != null) {
                    resultado.add(nombre.toLowerCase(Locale.ROOT));
                }
            }
        }
        return resultado;
    }

    private Set<String> restricciones(String tabla) throws SQLException {
        Set<String> resultado = new HashSet<>();
        try (var ps = connection.prepareStatement("""
                select constraint_name
                from information_schema.table_constraints
                where table_name = ?
                """)) {
            ps.setString(1, tabla.toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(rs.getString("constraint_name").toLowerCase(Locale.ROOT));
                }
            }
        }
        return resultado;
    }

    private long contarCambiosAplicados() throws SQLException {
        return primerLong("select count(*) from databasechangelog");
    }

    private long primerLong(String sql) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private void ejecutar(String sql) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }
}
