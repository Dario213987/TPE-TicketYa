package org.tudai.ticketya.web.user;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserContextMvcTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WhoAmIController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void conHeaders_elEndpointObtieneUsuarioYRol() throws Exception {
        mvc.perform(get("/test/yo").header("X-User-Id", "42").header("X-User-Role", "cliente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("42"))
                .andExpect(jsonPath("$.role").value("CLIENTE"));
    }

    @Test
    void sinHeaders_responde400ConMensajeClaro() throws Exception {
        mvc.perform(get("/test/yo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta el header obligatorio X-User-Id"));
    }

    @Test
    void rolInvalido_responde400() throws Exception {
        mvc.perform(get("/test/yo").header("X-User-Id", "42").header("X-User-Role", "superman"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Valores permitidos")));
    }

    @RestController
    static class WhoAmIController {

        @GetMapping("/test/yo")
        Map<String, String> yo(HttpServletRequest request) {
            UserContext user = new HeaderUserContext(request);
            return Map.of("userId", user.userId(), "role", user.role().name());
        }
    }
}