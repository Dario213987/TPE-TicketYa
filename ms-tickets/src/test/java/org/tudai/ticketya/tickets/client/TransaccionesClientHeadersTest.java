package org.tudai.ticketya.tickets.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica, con el contexto de Spring, que el cliente Feign de ms-transacciones manda los
 * headers del organizador aunque el request entrante sea de un cliente (el interceptor de
 * libs los reenvía y el de ms-tickets los tiene que reemplazar).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickets-feign;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransaccionesClientHeadersTest {

    private static final Map<String, List<String>> RECIBIDOS = new ConcurrentHashMap<>();
    private static final HttpServer SERVIDOR = iniciarServidor();

    @Autowired
    private TransaccionesApiClient transaccionesClient;

    @DynamicPropertySource
    static void apuntarAlServidor(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.transacciones.url",
                () -> "http://localhost:" + SERVIDOR.getAddress().getPort());
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
        RECIBIDOS.clear();
    }

    @AfterAll
    static void detener() {
        SERVIDOR.stop(0);
    }

    @Test
    void generarDebito_mandaLosHeadersDelOrganizadorEnLugarDeLosDelCliente() {
        MockHttpServletRequest entrante = new MockHttpServletRequest();
        entrante.addHeader("X-User-Id", "42");
        entrante.addHeader("X-User-Role", "CLIENTE");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(entrante));

        TransaccionesHeadersInterceptor.comoOrganizador(5L, () -> transaccionesClient.generarDebito(
                new GenerarTransaccionRequest().ticketId(1L).clienteId(42L).monto(1500.0)));

        assertThat(RECIBIDOS.get("X-user-id")).containsExactly("5");
        assertThat(RECIBIDOS.get("X-user-role")).containsExactly("ORGANIZADOR");
    }

    private static HttpServer iniciarServidor() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/api/transacciones/debitos", intercambio -> {
                RECIBIDOS.putAll(intercambio.getRequestHeaders());
                byte[] cuerpo = """
                        {"id":1,"tipo":"DEBITO","estado":"COMPLETA","monto":1500.0,"clienteId":42,
                         "organizadorId":5,"ticketId":1,"creadoEl":"2026-11-05T12:00:00Z",
                         "actualizadoEl":"2026-11-05T12:00:00Z"}
                        """.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(201, cuerpo.length);
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
