package org.tudai.ticketya.web.error;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.MdcKeys;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void bodyInvalido_devuelve400ConDetallePorCampo() throws Exception {
        mvc.perform(post("/test/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field=='nombre')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='cantidad')]").exists());
    }

    @Test
    void jsonMalformado_devuelve400() throws Exception {
        mvc.perform(post("/test/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void solicitudInvalidaDeNegocio_devuelve400() throws Exception {
        mvc.perform(get("/test/mala-solicitud"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("desde no puede ser mayor que hasta"));
    }

    @Test
    void recursoInexistente_devuelve404() throws Exception {
        mvc.perform(get("/test/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("Evento 99 no existe"));
    }

    @Test
    void conflictoDeNegocio_devuelve409() throws Exception {
        mvc.perform(get("/test/conflicto"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El asiento ya está reservado"));
    }

    @Test
    void accesoDenegado_devuelve403() throws Exception {
        mvc.perform(get("/test/prohibido"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void errorInesperado_devuelve500SinFiltrarDetalle() throws Exception {
        mvc.perform(get("/test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado. Intente nuevamente más tarde."))
                .andExpect(content().string(not(containsString("secreto"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void conCorrelationIdEnMdc_loIncluyeEnLaRespuesta() throws Exception {
        MDC.put(MdcKeys.CORRELATION_ID, "abc-123");

        mvc.perform(get("/test/no-existe"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void sinCorrelationId_noIncluyeElCampo() throws Exception {
        mvc.perform(get("/test/no-existe"))
                .andExpect(jsonPath("$.correlationId").doesNotExist());
    }

    @RestController
    static class TestController {

        record Body(@NotBlank String nombre, @Min(1) int cantidad) {}

        @PostMapping("/test/validar")
        void validar(@Valid @RequestBody Body body) {
        }

        @GetMapping("/test/mala-solicitud")
        void malaSolicitud() {
            throw new BadRequestException("desde no puede ser mayor que hasta");
        }

        @GetMapping("/test/no-existe")
        void noExiste() {
            throw new NotFoundException("Evento 99 no existe");
        }

        @GetMapping("/test/conflicto")
        void conflicto() {
            throw new ConflictException("El asiento ya está reservado");
        }

        @GetMapping("/test/prohibido")
        void prohibido() {
            throw new ForbiddenException("No tenés permiso para esta operación");
        }

        @GetMapping("/test/error")
        void error() {
            throw new IllegalStateException("detalle interno secreto");
        }
    }
}