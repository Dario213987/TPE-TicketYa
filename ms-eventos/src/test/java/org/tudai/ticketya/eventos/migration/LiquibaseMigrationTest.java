package org.tudai.ticketya.eventos.migration;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.password=ignored",
        "spring.liquibase.contexts=prod",
        "ticketya.eventos.finalizacion.enabled=false",
        "ticketya.eventos.notificaciones.enabled=false",
        "ticketya.logging.dir=target/test-logs"
})
@Testcontainers
class LiquibaseMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void elChangelogCompletoSeAplicaSobrePostgresql() {
        assertThat(tipoColumna(jdbcTemplate, "eventos", "fecha"))
                .isEqualTo("timestamp without time zone");
        assertThat(tipoColumna(jdbcTemplate, "evento_notificacion", "proximo_intento"))
                .isEqualTo("timestamp with time zone");
        assertThat(tipoColumna(jdbcTemplate, "evento_notificacion", "creado_el"))
                .isEqualTo("timestamp with time zone");
        assertThat(tipoColumna(jdbcTemplate, "evento_notificacion", "actualizado_el"))
                .isEqualTo("timestamp with time zone");

        Integer constraints = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM information_schema.table_constraints
                 WHERE table_schema = current_schema()
                   AND table_name = 'evento_notificacion'
                   AND constraint_name = 'uk_evento_notificacion_evento_tipo'
                   AND constraint_type = 'UNIQUE'
                """, Integer.class);
        assertThat(constraints).isEqualTo(1);
    }

    @Test
    void convierteLaFechaDateAlFinalDelDiaAlAplicar00012() throws Exception {
        String database = "fecha_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + database),
                postgres.getUsername(),
                postgres.getPassword());

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/test-00012-fecha.yaml");
        liquibase.afterPropertiesSet();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Timestamp fecha = jdbc.queryForObject(
                "SELECT fecha FROM eventos WHERE id = 1", Timestamp.class);

        assertThat(fecha).isNotNull();
        assertThat(fecha.toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 1, 15, 23, 59, 59));
        assertThat(tipoColumna(jdbc, "eventos", "fecha"))
                .isEqualTo("timestamp without time zone");
    }

    private static String tipoColumna(JdbcTemplate jdbc, String tabla, String columna) {
        return jdbc.queryForObject("""
                SELECT data_type
                  FROM information_schema.columns
                 WHERE table_schema = current_schema()
                   AND table_name = ?
                   AND column_name = ?
                """, String.class, tabla, columna);
    }
}
