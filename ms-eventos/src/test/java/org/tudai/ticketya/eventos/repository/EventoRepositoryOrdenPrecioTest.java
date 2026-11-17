package org.tudai.ticketya.eventos.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.TipoEvento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Sector;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.Ubicacion;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:eventos-precio-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "ticketya.eventos.finalizacion.enabled=false",
        "ticketya.eventos.notificaciones.enabled=false",
        "ticketya.logging.dir=target/test-logs"
})
class EventoRepositoryOrdenPrecioTest {

    private static final Set<EstadoEvento> ESTADOS_VISIBLES = Set.of(
            EstadoEvento.PUBLICADO, EstadoEvento.AGOTADO);

    @Autowired
    EventoRepository eventoRepository;

    @Autowired
    LugarRepository lugarRepository;

    @BeforeEach
    void limpiarBase() {
        eventoRepository.deleteAllInBatch();
        lugarRepository.deleteAll();
    }

    @Test
    void buscarPorPrecio_usaElMinimoDeLosPreciosDelEventoYNoElPrecioBaseDelLugar() {
        Lugar lugarBarato = guardarLugar("lugar-barato", 10.0);
        Lugar lugarCaro = guardarLugar("lugar-caro", 1000.0);

        Evento eventoCaro = guardarEvento("evento-caro", lugarBarato, 500.0);
        Evento eventoBarato = guardarEvento("evento-barato", lugarCaro, 100.0);

        Page<Evento> pagina = eventoRepository.buscarPorPrecio(
                eventosVisibles(), PageRequest.of(0, 10));

        assertThat(pagina.getContent())
                .extracting(Evento::getNombre)
                .containsExactly(eventoBarato.getNombre(), eventoCaro.getNombre());
        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }

    @Test
    void buscarPorPrecio_usaElMinimoEntreVariosSectoresYDesempataPorId() {
        Lugar lugar = guardarLugarConDosSectores(10.0, 20.0);
        Sector primero = lugar.getSectores().stream()
                .filter(sector -> sector.getPrecio() == 10.0)
                .findFirst()
                .orElseThrow();
        Sector segundo = lugar.getSectores().stream()
                .filter(sector -> sector.getPrecio() == 20.0)
                .findFirst()
                .orElseThrow();

        Evento primeroPorId = guardarEvento("primero-por-id", lugar);
        primeroPorId.definirPrecioSector(primero.getId(), 300.0);
        primeroPorId.definirPrecioSector(segundo.getId(), 150.0);
        eventoRepository.save(primeroPorId);

        Evento segundoPorId = guardarEvento("segundo-por-id", lugar);
        segundoPorId.definirPrecioSector(primero.getId(), 150.0);
        eventoRepository.save(segundoPorId);

        Page<Evento> pagina = eventoRepository.buscarPorPrecio(
                eventosVisibles(), PageRequest.of(0, 10));

        assertThat(pagina.getContent())
                .extracting(Evento::getNombre)
                .containsExactly(primeroPorId.getNombre(), segundoPorId.getNombre());
    }

    @Test
    void buscarPorPrecio_dejaAlFinalLosEventosSinPrecio() {
        Lugar lugar = guardarLugar("lugar", 10.0);
        Evento sinPrecio = guardarEvento("sin-precio", lugar);
        Evento conPrecio = guardarEvento("con-precio", lugar, 250.0);

        Page<Evento> pagina = eventoRepository.buscarPorPrecio(
                eventosVisibles(), PageRequest.of(0, 10));

        assertThat(pagina.getContent())
                .extracting(Evento::getNombre)
                .containsExactly(conPrecio.getNombre(), sinPrecio.getNombre());
    }

    private Specification<Evento> eventosVisibles() {
        return (root, query, criteriaBuilder) -> root.get("estado").in(ESTADOS_VISIBLES);
    }

    private Lugar guardarLugar(String nombre, double precio) {
        Lugar lugar = Lugar.builder()
                .nombre(nombre)
                .tipo("TEATRO")
                .direccion("Calle de prueba 123")
                .ciudad("Tandil")
                .ubicacion(Ubicacion.builder().latitud(-37.32).longitud(-59.14).build())
                .build();
        lugar.addSector(sector(precio));
        return lugarRepository.saveAndFlush(lugar);
    }

    private Lugar guardarLugarConDosSectores(double primerPrecio, double segundoPrecio) {
        Lugar lugar = Lugar.builder()
                .nombre("lugar-dos-sectores")
                .tipo("TEATRO")
                .direccion("Calle de prueba 456")
                .ciudad("Tandil")
                .ubicacion(Ubicacion.builder().latitud(-37.32).longitud(-59.14).build())
                .build();
        lugar.addSector(sector(primerPrecio));
        lugar.addSector(sector(segundoPrecio));
        return lugarRepository.saveAndFlush(lugar);
    }

    private Sector sector(double precio) {
        return SectorNoNumerado.builder()
                .nombre("Sector " + precio)
                .precio(precio)
                .capacidadMaxima(100)
                .build();
    }

    private Evento guardarEvento(String nombre, Lugar lugar, double precio) {
        Evento evento = guardarEvento(nombre, lugar);
        evento.definirPrecioSector(lugar.getSectores().iterator().next().getId(), precio);
        return eventoRepository.saveAndFlush(evento);
    }

    private Evento guardarEvento(String nombre, Lugar lugar) {
        return eventoRepository.save(Evento.builder()
                .nombre(nombre)
                .tipo(TipoEvento.RECITAL)
                .fecha(LocalDateTime.of(2027, 1, 1, 20, 0))
                .estado(EstadoEvento.PUBLICADO)
                .organizadorId(7L)
                .lugar(lugar)
                .build());
    }
}
