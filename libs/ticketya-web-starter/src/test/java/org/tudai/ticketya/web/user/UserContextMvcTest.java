package org.tudai.ticketya.web.user;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.InvalidUserContextException;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
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
    void sinHeaders_responde401ConMensajeClaro() throws Exception {
        mvc.perform(get("/test/yo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Falta el header obligatorio X-User-Id"));
    }

    @Test
    void rolInvalido_responde403() throws Exception {
        mvc.perform(get("/test/yo").header("X-User-Id", "42").header("X-User-Role", "superman"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Valores permitidos")));
    }

    @Test
    void findUserId_conHeaderValido_devuelveElId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "42");

        assertThat(new HeaderUserContext(request).findUserId()).contains(42L);
    }

    @Test
    void findUserId_sinHeader_devuelveVacioEnVezDeLanzar() {
        UserContext user = new HeaderUserContext(new MockHttpServletRequest());

        assertThat(user.findUserId()).isEmpty();
    }

    @Test
    void findUserId_conIdNoNumerico_devuelveVacio() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "abc");

        assertThat(new HeaderUserContext(request).findUserId()).isEmpty();
    }

    @Test
    void userId_conIdNoNumerico_lanzaInvalidUserContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "abc");

        assertThatThrownBy(() -> new HeaderUserContext(request).userId())
                .isInstanceOf(InvalidUserContextException.class);
    }

    @Test
    void conUsuarioPeroSinRol_responde401() throws Exception {
        mvc.perform(get("/test/yo").header("X-User-Id", "42"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class WhoAmIController {

        @GetMapping("/test/yo")
        Map<String, String> yo(HttpServletRequest request) {
            UserContext user = new HeaderUserContext(request);
            return Map.of("userId", user.userId().toString(), "role", user.role().name());
        }
    }
}