package org.tudai.ticketya.tickets.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickets-schema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml",
        "transacciones.name=transacciones"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TicketsSchemaValidationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void elEsquemaDeLiquibaseCoincideConLasEntidadesJpa() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from databasechangelog", Long.class)).isEqualTo(5L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tickets", Long.class)).isZero();
    }
}
