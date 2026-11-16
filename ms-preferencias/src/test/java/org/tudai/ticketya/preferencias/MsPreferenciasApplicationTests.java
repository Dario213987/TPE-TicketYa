package org.tudai.ticketya.preferencias;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "preferencias.historial.consumer-enabled=false")
class MsPreferenciasApplicationTests {

    @Test
    void contextLoads() {
    }

}
