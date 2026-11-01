package org.tudai.ticketya.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.web.correlation.CorrelationIdFilter;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CorrelationIdMvcTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addFilters(new CorrelationIdFilter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void elErrorLlevaElIdRecibido_enElHeaderYEnElCuerpo() throws Exception {
        mvc.perform(get("/test/no-existe").header("X-Correlation-Id", "abc-123"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-Id", "abc-123"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void sinHeader_elIdGeneradoCoincideEnHeaderYCuerpo() throws Exception {
        MvcResult result = mvc.perform(get("/test/no-existe")).andReturn();

        String id = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(id).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains("\"correlationId\":\"" + id + "\"");
    }

    @RestController
    static class TestController {
        @GetMapping("/test/no-existe")
        void noExiste() {
            throw new NotFoundException("no existe");
        }
    }
}
