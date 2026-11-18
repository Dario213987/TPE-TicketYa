package org.tudai.ticketya.tickets.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PagedModel;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.TipoTransaccion;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica, con el contexto de Spring, que el cliente Feign generado de ms-transacciones puede
 * leer la página de {@code GET /api/transacciones/ticket/{ticketId}}: el contrato la mapea a
 * {@link PagedModel} de Spring Data, que sin {@code PagedModelJacksonConfig} no se deserializa
 * (TKY-87, la usa la reconciliación).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickets-feign-pagina;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransaccionesClientBuscarPorTicketTest {

    private static final AtomicReference<String> CONSULTA = new AtomicReference<>();
    private static final HttpServer SERVIDOR = iniciarServidor();

    @Autowired
    private TransaccionesApiClient transaccionesClient;

    @DynamicPropertySource
    static void apuntarAlServidor(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.transacciones.url",
                () -> "http://localhost:" + SERVIDOR.getAddress().getPort());
    }

    @AfterAll
    static void detener() {
        SERVIDOR.stop(0);
    }

    @Test
    void buscarPorTicket_leeLaPaginaConSusTransacciones() {
        PagedModel<?> pagina = TransaccionesHeadersInterceptor.comoOrganizador(5L,
                () -> transaccionesClient.buscarPorTicket(10L, TipoTransaccion.DEBITO, 0, 100, null));

        assertThat(CONSULTA.get()).contains("/api/transacciones/ticket/10").contains("tipo=DEBITO");
        assertThat(pagina.getMetadata().totalElements()).isEqualTo(1);
        assertThat(pagina.getContent()).singleElement()
                .isInstanceOfSatisfying(Map.class, transaccion -> {
                    assertThat(transaccion.get("estado")).isEqualTo("COMPLETA");
                    assertThat(transaccion.get("clienteId")).isEqualTo(42);
                });
    }

    private static HttpServer iniciarServidor() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/api/transacciones/ticket", intercambio -> {
                CONSULTA.set(intercambio.getRequestURI().toString());
                byte[] cuerpo = """
                        {"content":[{"id":7,"tipo":"DEBITO","estado":"COMPLETA","monto":1500.0,"clienteId":42,
                          "organizadorId":5,"ticketId":10,"creadoEl":"2026-11-05T12:00:00Z",
                          "actualizadoEl":"2026-11-05T12:00:00Z"}],
                         "page":{"size":100,"number":0,"totalElements":1,"totalPages":1}}
                        """.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, cuerpo.length);
                intercambio.getResponseBody().write(cuerpo);
                intercambio.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
