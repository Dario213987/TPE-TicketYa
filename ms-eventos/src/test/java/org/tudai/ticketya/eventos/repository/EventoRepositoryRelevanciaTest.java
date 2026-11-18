package org.tudai.ticketya.eventos.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:eventos-relevancia-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "ticketya.eventos.finalizacion.enabled=false",
        "ticketya.eventos.notificaciones.enabled=false",
        "ticketya.logging.dir=target/test-logs"
})
class EventoRepositoryRelevanciaTest {

    private static final Set<EstadoEvento> ESTADOS_VISIBLES = Set.of(
            EstadoEvento.PUBLICADO, EstadoEvento.AGOTADO);

    @Autowired
    EventoRepository eventoRepository;

    @BeforeEach
    void limpiarBase() {
        eventoRepository.deleteAllInBatch();
    }

    @Test
    void buscarPorRelevancia_priorizaPublicadosYLuegoAgotadosConFechaEIdComoDesempate() {
        LocalDateTime base = LocalDateTime.of(2027, 1, 1, 20, 0);
        Evento agotado = guardar("agotado", base.plusDays(1), EstadoEvento.AGOTADO);
        Evento publicadosTarde = guardar("publicado-tarde", base.plusDays(3), EstadoEvento.PUBLICADO);
        Evento publicadosTemprano = guardar("publicado-temprano", base.plusDays(2), EstadoEvento.PUBLICADO);
        Evento publicadosMismoDia = guardar("publicado-mismo-dia", base.plusDays(2), EstadoEvento.PUBLICADO);
        guardar("borrador", base, EstadoEvento.BORRADOR);

        Specification<Evento> visibles = (root, query, criteriaBuilder) ->
                root.get("estado").in(ESTADOS_VISIBLES);

        Page<Evento> pagina = eventoRepository.buscarPorRelevancia(
                visibles, PageRequest.of(0, 10));

        assertThat(pagina.getContent())
                .extracting(Evento::getNombre)
                .containsExactly(
                        publicadosTemprano.getNombre(),
                        publicadosMismoDia.getNombre(),
                        publicadosTarde.getNombre(),
                        agotado.getNombre());
        assertThat(pagina.getTotalElements()).isEqualTo(4);
    }

    private Evento guardar(String nombre, LocalDateTime fecha, EstadoEvento estado) {
        return eventoRepository.save(Evento.builder()
                .nombre(nombre)
                .fecha(fecha)
                .estado(estado)
                .organizadorId(7L)
                .build());
    }
}
