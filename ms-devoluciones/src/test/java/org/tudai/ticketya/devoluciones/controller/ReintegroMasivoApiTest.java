package org.tudai.ticketya.devoluciones.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.tudai.ticketya.devoluciones.service.ReintegroMasivoService;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "ticketya.logging.dir=target/test-logs"
})
@AutoConfigureMockMvc
class ReintegroMasivoApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReintegroMasivoService reintegroMasivoService;

    @Test
    @DisplayName("PUT /api/devoluciones/eventos/{id}/reintegro-masivo registra el proceso sin usuario y responde 202")
    void reintegrarEvento_registraProceso() throws Exception {
        mvc.perform(put("/api/devoluciones/eventos/500/reintegro-masivo"))
                .andExpect(status().isAccepted());

        verify(reintegroMasivoService).iniciar(500L);
    }
}
