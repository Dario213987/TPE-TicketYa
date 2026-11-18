package org.tudai.ticketya.preferencias.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.tudai.ticketya.preferencias.service.DestinatariosService;
import org.tudai.ticketya.web.TicketyaWebAutoConfiguration;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreferenciaInternalController.class)
@Import(TicketyaWebAutoConfiguration.class)
class PreferenciaInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DestinatariosService destinatariosService;

    @Test
    void obtener_conParticipantesYCiudad_devuelveLosUsuarioIdsSinHeaders() throws Exception {
        given(destinatariosService.obtenerDestinatarios(List.of(15L, 20L), "Tándil"))
                .willReturn(List.of(7L, 12L));

        mockMvc.perform(get("/api/preferencias/internal/destinatarios")
                        .param("participanteIds", "15")
                        .param("participanteIds", "20")
                        .param("ciudad", "Tándil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(7))
                .andExpect(jsonPath("$[1]").value(12));

        verify(destinatariosService).obtenerDestinatarios(List.of(15L, 20L), "Tándil");
    }

    @Test
    void obtener_sinParticipantes_devuelveListaVacia() throws Exception {
        given(destinatariosService.obtenerDestinatarios(any(), eq("Tandil")))
                .willReturn(List.of());

        mockMvc.perform(get("/api/preferencias/internal/destinatarios")
                        .param("ciudad", "Tandil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(destinatariosService).obtenerDestinatarios(isNull(), eq("Tandil"));
    }

    @Test
    void obtener_conCiudadVacia_devuelve400() throws Exception {
        mockMvc.perform(get("/api/preferencias/internal/destinatarios")
                        .param("participanteIds", "15")
                        .param("ciudad", "   "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(destinatariosService);
    }

    @Test
    void obtener_conIdDeParticipanteInvalido_devuelve400() throws Exception {
        mockMvc.perform(get("/api/preferencias/internal/destinatarios")
                        .param("participanteIds", "0")
                        .param("ciudad", "Tandil"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(destinatariosService);
    }
}
