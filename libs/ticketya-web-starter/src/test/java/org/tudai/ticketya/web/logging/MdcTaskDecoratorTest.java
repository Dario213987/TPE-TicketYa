package org.tudai.ticketya.web.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @Test
    void copiaElContextoAlHiloDelExecutor() throws Exception {
        MDC.put("correlationId", "abc-123");
        AtomicReference<String> visto = new AtomicReference<>();

        Thread hilo = new Thread(decorator.decorate(() -> visto.set(MDC.get("correlationId"))));
        hilo.start();
        hilo.join();

        assertEquals("abc-123", visto.get());
    }

    @Test
    void elContextoDelWorkerNoAfectaAlHiloQueAgenda() throws Exception {
        MDC.put("correlationId", "externo");
        AtomicReference<String> dentro = new AtomicReference<>();

        Thread hilo = new Thread(decorator.decorate(() -> {
            dentro.set(MDC.get("correlationId"));
            MDC.put("correlationId", "interno");
        }));
        hilo.start();
        hilo.join();

        assertEquals("externo", dentro.get());
        assertEquals("externo", MDC.get("correlationId"));
    }

    @Test
    void limpiaElContextoDelWorkerCuandoElOrigenNoTieneMdc() throws Exception {
        MDC.clear();
        AtomicReference<String> dentro = new AtomicReference<>();

        Thread hilo = new Thread(decorator.decorate(() -> dentro.set(MDC.get("correlationId"))));
        MDC.put("correlationId", "solo-origen");
        hilo.start();
        hilo.join();

        assertNull(dentro.get());
    }
}
