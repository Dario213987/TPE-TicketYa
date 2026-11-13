package org.tudai.ticketya.preferencias.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.tudai.ticketya.contract.preferencias.model.PreferenciaRequestDTO;
import org.tudai.ticketya.contract.preferencias.model.PreferenciaResponseDTO;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;
import org.tudai.ticketya.preferencias.service.PreferenciaService;
import org.tudai.ticketya.web.TicketyaWebAutoConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreferenciaController.class)
@Import(TicketyaWebAutoConfiguration.class)
class PreferenciaControllerTest {

    private static final String CLIENTE_ID = "7";
    private static final String ROL_CLIENTE = "CLIENTE";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreferenciaService preferenciaService;

    @Test
    void obtener_sinPreferencias_devuelve200ConListasVacias() throws Exception {
        given(preferenciaService.obtener(7L)).willReturn(new PreferenciaResponseDTO()
                .clienteId(7L)
                .tiposFavoritos(List.of())
                .artistasFavoritos(List.of())
                .ciudad(null));

        mockMvc.perform(get("/api/preferencias/mias")
                        .header("X-User-Id", CLIENTE_ID)
                        .header("X-User-Role", ROL_CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(7))
                .andExpect(jsonPath("$.tiposFavoritos").isArray())
                .andExpect(jsonPath("$.tiposFavoritos").isEmpty())
                .andExpect(jsonPath("$.artistasFavoritos").isEmpty());
    }

    @Test
    void obtener_sinHeaders_devuelve401() throws Exception {
        mockMvc.perform(get("/api/preferencias/mias"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(preferenciaService);
    }

    @Test
    void obtener_conRolQueNoEsCliente_devuelve403() throws Exception {
        mockMvc.perform(get("/api/preferencias/mias")
                        .header("X-User-Id", CLIENTE_ID)
                        .header("X-User-Role", "ORGANIZADOR"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(preferenciaService);
    }

    @Test
    void guardar_devuelve200YUsaElClienteDelHeader() throws Exception {
        given(preferenciaService.guardar(eq(7L), any(PreferenciaRequestDTO.class)))
                .willReturn(new PreferenciaResponseDTO()
                        .clienteId(7L)
                        .tiposFavoritos(List.of(TipoEvento.RECITAL))
                        .artistasFavoritos(List.of(15L))
                        .ciudad("Tandil"));

        mockMvc.perform(put("/api/preferencias/mias")
                        .header("X-User-Id", CLIENTE_ID)
                        .header("X-User-Role", ROL_CLIENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tiposFavoritos": ["RECITAL"],
                                  "artistasFavoritos": [15],
                                  "ciudad": "Tandil"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(7))
                .andExpect(jsonPath("$.tiposFavoritos[0]").value("RECITAL"))
                .andExpect(jsonPath("$.artistasFavoritos[0]").value(15))
                .andExpect(jsonPath("$.ciudad").value("Tandil"));

        verify(preferenciaService).guardar(eq(7L), any(PreferenciaRequestDTO.class));
    }

    @Test
    void guardar_conTipoDesconocido_devuelve400() throws Exception {
        mockMvc.perform(put("/api/preferencias/mias")
                        .header("X-User-Id", CLIENTE_ID)
                        .header("X-User-Role", ROL_CLIENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tiposFavoritos": ["NO_EXISTE"],
                                  "artistasFavoritos": [15],
                                  "ciudad": "Tandil"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(preferenciaService);
    }

    @Test
    void guardar_conArtistaCero_devuelve400() throws Exception {
        mockMvc.perform(put("/api/preferencias/mias")
                        .header("X-User-Id", CLIENTE_ID)
                        .header("X-User-Role", ROL_CLIENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tiposFavoritos": ["RECITAL"],
                                  "artistasFavoritos": [0],
                                  "ciudad": "Tandil"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(preferenciaService);
    }

    @Test
    void guardar_conCiudadVacia_devuelve400ConElDetalleDelCampo() throws Exception {        mockMvc.perform(put("/api/preferencias/mias")
                        .header("X-User-Id", CLIENTE_ID)
                        .header("X-User-Role", ROL_CLIENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tiposFavoritos": ["RECITAL"],
                                  "artistasFavoritos": [15],
                                  "ciudad": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ciudad"));

        verifyNoInteractions(preferenciaService);
    }

    @Test
    void guardar_sinListas_equivaleAEnviarlasVacias() throws Exception {
        given(preferenciaService.guardar(eq(7L), any(PreferenciaRequestDTO.class)))
                .willReturn(new PreferenciaResponseDTO()
                        .clienteId(7L)
                        .tiposFavoritos(List.of())
                        .artistasFavoritos(List.of())
                        .ciudad("Tandil"));

        mockMvc.perform(put("/api/preferencias/mias")
                        .header("X-User-Id", CLIENTE_ID)
                        .header("X-User-Role", ROL_CLIENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ciudad": "Tandil"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposFavoritos").isEmpty())
                .andExpect(jsonPath("$.artistasFavoritos").isEmpty());

        ArgumentCaptor<PreferenciaRequestDTO> captor =
                ArgumentCaptor.forClass(PreferenciaRequestDTO.class);
        verify(preferenciaService).guardar(eq(7L), captor.capture());
        assertThat(captor.getValue().getTiposFavoritos()).isEmpty();
        assertThat(captor.getValue().getArtistasFavoritos()).isEmpty();
    }
}
