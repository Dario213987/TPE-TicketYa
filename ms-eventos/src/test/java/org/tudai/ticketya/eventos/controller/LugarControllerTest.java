package org.tudai.ticketya.eventos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.data.web.config.SpringDataJacksonConfiguration.PageModule;
import org.springframework.data.web.config.SpringDataWebSettings;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.dto.lugar.LugarCreateDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarDetalleResponseDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.service.LugarService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LugarControllerTest {

    private LugarService lugarService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        lugarService = mock(LugarService.class);

        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.registerModule(new PageModule(new SpringDataWebSettings(PageSerializationMode.VIA_DTO)));

        mvc = MockMvcBuilders.standaloneSetup(new LugarController(lugarService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void crearLugar_conDatosValidos_devuelve201() throws Exception {
        LugarResponseDTO response = LugarResponseDTO.builder()
                .id(5L)
                .nombre("Estadio Municipal")
                .tipo("ESTADIO")
                .direccion("Av. Siempre Viva 742")
                .ciudad("Rosario")
                .latitud(-32.95)
                .longitud(-60.64)
                .build();
        when(lugarService.crearLugar(any(LugarCreateDTO.class))).thenReturn(response);

        mvc.perform(post("/api/lugares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Estadio Municipal",
                                  "tipo": "ESTADIO",
                                  "direccion": "Av. Siempre Viva 742",
                                  "ciudad": "Rosario",
                                  "latitud": -32.95,
                                  "longitud": -60.64,
                                  "capacidad": 30000
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.nombre").value("Estadio Municipal"))
                .andExpect(jsonPath("$.latitud").value(-32.95))
                .andExpect(jsonPath("$.longitud").value(-60.64));
    }

    @Test
    void crearLugar_conDatosInvalidos_devuelve400ConElDetalleDeLosCampos() throws Exception {
        mvc.perform(post("/api/lugares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "",
                                  "tipo": "ESTADIO",
                                  "direccion": "Av. Siempre Viva 742",
                                  "ciudad": "Rosario",
                                  "latitud": 100
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors[?(@.field=='nombre')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='latitud')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='longitud')]").exists());
    }

    @Test
    void listarLugares_devuelveLaPaginaDelServicio() throws Exception {
        LugarDetalleResponseDTO lugar = LugarDetalleResponseDTO.builder()
                .id(1L)
                .nombre("Estadio Municipal")
                .ciudad("Rosario")
                .latitud(-32.95)
                .longitud(-60.64)
                .sectores(List.of())
                .build();
        when(lugarService.listarLugares(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lugar)));

        mvc.perform(get("/api/lugares"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nombre").value("Estadio Municipal"))
                .andExpect(jsonPath("$.content[0].ciudad").value("Rosario"));
    }

    @Test
    void listarLugares_conCiudadLaPasaAlServicio() throws Exception {
        when(lugarService.listarLugares(eq("Rosario"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/lugares").param("ciudad", "Rosario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void obtenerLugar_existente_devuelve200() throws Exception {
        LugarDetalleResponseDTO response = LugarDetalleResponseDTO.builder()
                .id(3L)
                .nombre("Teatro El Círculo")
                .ciudad("Rosario")
                .latitud(-32.95)
                .longitud(-60.64)
                .sectores(List.of())
                .build();
        when(lugarService.obtenerLugar(3L)).thenReturn(response);

        mvc.perform(get("/api/lugares/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.nombre").value("Teatro El Círculo"));
    }

    @Test
    void obtenerLugar_inexistente_devuelve404() throws Exception {
        when(lugarService.obtenerLugar(99L))
                .thenThrow(new NotFoundException("Lugar no encontrado con id: 99"));

        mvc.perform(get("/api/lugares/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("Lugar no encontrado con id: 99"));
    }
}
