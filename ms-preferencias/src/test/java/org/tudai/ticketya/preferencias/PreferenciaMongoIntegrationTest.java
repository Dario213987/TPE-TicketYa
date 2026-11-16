package org.tudai.ticketya.preferencias;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tudai.ticketya.contract.preferencias.model.PreferenciaRequestDTO;
import org.tudai.ticketya.contract.preferencias.model.PreferenciaResponseDTO;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;
import org.tudai.ticketya.preferencias.service.PreferenciaService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el contrato real con MongoDB.
 * <p>
 * Se omite automáticamente cuando no hay Docker disponible.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class PreferenciaMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    private PreferenciaService preferenciaService;

    @Autowired
    private PreferenciaRepository preferenciaRepository;

    @BeforeEach
    void limpiarColeccion() {
        preferenciaRepository.deleteAll();
    }

    @Test
    void guardar_persisteYReemplazaElDocumentoDelCliente() {
        preferenciaService.guardar(7L, request(
                List.of(TipoEvento.RECITAL), List.of(15L), "Tandil"));

        PreferenciaResponseDTO primera = preferenciaService.obtener(7L);
        assertThat(primera.getTiposFavoritos()).containsExactly(TipoEvento.RECITAL);
        assertThat(primera.getArtistasFavoritos()).containsExactly(15L);
        assertThat(primera.getCiudad()).isEqualTo("Tandil");

        preferenciaService.guardar(7L, request(
                List.of(TipoEvento.TEATRO), List.of(20L), "Rauch"));

        PreferenciaResponseDTO segunda = preferenciaService.obtener(7L);
        assertThat(segunda.getTiposFavoritos()).containsExactly(TipoEvento.TEATRO);
        assertThat(segunda.getArtistasFavoritos()).containsExactly(20L);
        assertThat(segunda.getCiudad()).isEqualTo("Rauch");
        assertThat(preferenciaRepository.count()).isEqualTo(1);
    }

    @Test
    void obtener_sinDocumento_devuelveListasVaciasSinCrearlo() {
        PreferenciaResponseDTO respuesta = preferenciaService.obtener(7L);

        assertThat(respuesta.getClienteId()).isEqualTo(7L);
        assertThat(respuesta.getTiposFavoritos()).isEmpty();
        assertThat(respuesta.getArtistasFavoritos()).isEmpty();
        assertThat(respuesta.getCiudad()).isNull();
        assertThat(preferenciaRepository.count()).isZero();
    }

    @Test
    void guardar_deDosClientes_creaUnDocumentoPorCliente() {
        preferenciaService.guardar(7L, request(
                List.of(TipoEvento.RECITAL), List.of(15L), "Tandil"));
        preferenciaService.guardar(8L, request(
                List.of(TipoEvento.FESTIVAL), List.of(20L), "Rauch"));

        assertThat(preferenciaRepository.count()).isEqualTo(2);
        assertThat(preferenciaService.obtener(7L).getTiposFavoritos())
                .containsExactly(TipoEvento.RECITAL);
        assertThat(preferenciaService.obtener(8L).getTiposFavoritos())
                .containsExactly(TipoEvento.FESTIVAL);
    }

    private PreferenciaRequestDTO request(List<TipoEvento> tipos, List<Long> artistas, String ciudad) {
        return new PreferenciaRequestDTO()
                .tiposFavoritos(tipos)
                .artistasFavoritos(artistas)
                .ciudad(ciudad);
    }
}
