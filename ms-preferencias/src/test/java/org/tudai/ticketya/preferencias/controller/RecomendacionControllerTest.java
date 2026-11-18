package org.tudai.ticketya.preferencias.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.tudai.ticketya.contract.preferencias.model.MotivoRecomendacion;
import org.tudai.ticketya.contract.preferencias.model.RecomendacionResponseDTO;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;
import org.tudai.ticketya.preferencias.service.RecomendacionEndpointService;
import org.tudai.ticketya.web.TicketyaWebAutoConfiguration;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecomendacionController.class)
@Import(TicketyaWebAutoConfiguration.class)
class RecomendacionControllerTest {

    private static final String CLIENTE_ID = "7";
    private static final String ROL_CLIENTE = "CLIENTE";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecomendacionEndpointService recomendacionEndpointService;

    @Test
    void recomendar_conCliente_devuelveLaListaConMotivoYPuntaje() throws Exception {
        given(recomendacionEndpointService.recomendar(7L)).willReturn(List.of(
                new RecomendacionResponseDTO()
                        .eventoId(5L)
                        .nombre("Recital")
                        .tipo(TipoEvento.RECITAL)
                        .fecha(LocalDateTime.of(2026, 12, 1, 20, 0))
                        .estado("PUBLICADO")
                        .ciudad("Tandil")
                        .motivo(MotivoRecomendacion.ARTISTA)
                        .puntaje(5)));

        mockMvc.perform(get("/api/recomendaciones")
                        .header("X-User-Id", CLIENTE_ID)
                        .header("X-User-Role", ROL_CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventoId").value(5))
                .andExpect(jsonPath("$[0].nombre").value("Recital"))
                .andExpect(jsonPath("$[0].tipo").value("RECITAL"))
                .andExpect(jsonPath("$[0].ciudad").value("Tandil"))
                .andExpect(jsonPath("$[0].motivo").value("ARTISTA"))
                .andExpect(jsonPath("$[0].puntaje").value(5));

        verify(recomendacionEndpointService).recomendar(7L);
    }

    @Test
    void recomendar_sinHeaders_devuelve401() throws Exception {
        mockMvc.perform(get("/api/recomendaciones"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recomendacionEndpointService);
    }

    @Test
    void recomendar_conRolQueNoEsCliente_devuelve403() throws Exception {
        mockMvc.perform(get("/api/recomendaciones")
                        .header("X-User-Id", CLIENTE_ID)
                        .header("X-User-Role", "ORGANIZADOR"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(recomendacionEndpointService);
    }
}
