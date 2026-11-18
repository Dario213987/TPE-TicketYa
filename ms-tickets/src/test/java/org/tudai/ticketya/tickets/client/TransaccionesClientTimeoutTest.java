package org.tudai.ticketya.tickets.client;

import com.sun.net.httpserver.HttpServer;
import feign.RetryableException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica, con el contexto de Spring, que el cliente de ms-transacciones corta por su propio
 * read-timeout ({@code spring.cloud.openfeign.client.config.transacciones.read-timeout}), que
 * pisa el valor por defecto de libs, y que {@link ReintentoTransacciones} vuelve a intentar
 * (TKY-85). El servidor tarda más que el timeout en responder.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tickets-feign-timeout;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.openfeign.client.config.transacciones.read-timeout=300",
        "tickets.transacciones.max-intentos=2",
        "tickets.transacciones.backoff-inicial-ms=1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransaccionesClientTimeoutTest {

    private static final long DEMORA_SERVIDOR_MS = 2000;
    private static final AtomicInteger LLAMADAS = new AtomicInteger();
    private static final HttpServer SERVIDOR = iniciarServidor();

    @Autowired
    private TransaccionesApiClient transaccionesClient;

    @Autowired
    private ReintentoTransacciones reintento;

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
    void generarDebito_servidorLento_cortaPorTimeoutYReintentaUnaVez() {
        GenerarTransaccionRequest request = new GenerarTransaccionRequest().ticketId(1L).clienteId(42L).monto(1500.0);
        long inicio = System.nanoTime();

        assertThatThrownBy(() -> reintento.ejecutar(() -> TransaccionesHeadersInterceptor.comoOrganizador(5L,
                () -> transaccionesClient.generarDebito(request))))
                .isInstanceOf(RetryableException.class);

        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
        assertThat(LLAMADAS.get()).isEqualTo(2);
        // Dos intentos de 300 ms: muy por debajo de lo que tarda el servidor en responder.
        assertThat(duracionMs).isLessThan(DEMORA_SERVIDOR_MS);
    }

    private static HttpServer iniciarServidor() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.setExecutor(Executors.newCachedThreadPool());
            servidor.createContext("/api/transacciones/debitos", intercambio -> {
                LLAMADAS.incrementAndGet();
                try {
                    Thread.sleep(DEMORA_SERVIDOR_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                intercambio.sendResponseHeaders(504, -1);
                intercambio.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
