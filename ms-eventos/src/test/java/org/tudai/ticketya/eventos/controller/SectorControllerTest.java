package org.tudai.ticketya.eventos.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.domain.TipoSector;
import org.tudai.ticketya.eventos.dto.sector.SectorNoNumeradoRequestDTO;
import org.tudai.ticketya.eventos.dto.sector.SectorNumeradoRequestDTO;
import org.tudai.ticketya.eventos.dto.sector.SectorResponseDTO;
import org.tudai.ticketya.eventos.service.SectorService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SectorControllerTest {

    private SectorService sectorService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        sectorService = mock(SectorService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SectorController(sectorService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void crearSectorNumerado_devuelve201() throws Exception {
        SectorResponseDTO response = SectorResponseDTO.builder()
                .id(10L)
                .nombre("Platea Alta")
                .precio(20000.0)
                .tipoSector(TipoSector.NUMERADO)
                .capacidad(6)
                .filas(2)
                .asientosPorFila(3)
                .build();
        when(sectorService.crearSectorNumerado(eq(1L), any(SectorNumeradoRequestDTO.class)))
                .thenReturn(response);

        mvc.perform(post("/api/lugares/1/sectores/numerados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Platea Alta",
                                  "precio": 20000.0,
                                  "filas": 2,
                                  "asientosPorFila": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.tipoSector").value(TipoSector.NUMERADO))
                .andExpect(jsonPath("$.capacidad").value(6));
    }

    @Test
    void crearSectorNoNumerado_devuelve201() throws Exception {
        SectorResponseDTO response = SectorResponseDTO.builder()
                .id(11L)
                .nombre("Campo")
                .precio(15000.0)
                .tipoSector(TipoSector.NO_NUMERADO)
                .capacidad(500)
                .capacidadMaxima(500)
                .build();
        when(sectorService.crearSectorNoNumerado(eq(1L), any(SectorNoNumeradoRequestDTO.class)))
                .thenReturn(response);

        mvc.perform(post("/api/lugares/1/sectores/no-numerados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Campo",
                                  "precio": 15000.0,
                                  "capacidadMaxima": 500
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.tipoSector").value(TipoSector.NO_NUMERADO))
                .andExpect(jsonPath("$.capacidadMaxima").value(500));
    }

    @Test
    void crearSectorNumerado_conDatosInvalidos_devuelve400() throws Exception {
        mvc.perform(post("/api/lugares/1/sectores/numerados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "",
                                  "precio": -1,
                                  "filas": 0,
                                  "asientosPorFila": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors[?(@.field=='nombre')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='precio')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='filas')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='asientosPorFila')]").exists());
    }

    @Test
    void crearSectorNoNumerado_conLugarInexistente_devuelve404() throws Exception {
        when(sectorService.crearSectorNoNumerado(eq(99L), any(SectorNoNumeradoRequestDTO.class)))
                .thenThrow(new NotFoundException("Lugar no encontrado con id: 99"));

        mvc.perform(post("/api/lugares/99/sectores/no-numerados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Campo",
                                  "precio": 15000.0,
                                  "capacidadMaxima": 500
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Lugar no encontrado con id: 99"));
    }
}
