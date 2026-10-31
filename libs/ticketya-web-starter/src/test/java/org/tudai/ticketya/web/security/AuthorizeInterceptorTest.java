package org.tudai.ticketya.web.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;
import org.tudai.ticketya.web.user.HeaderUserContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorizeInterceptorTest {

    private static final UserContext CURRENT_REQUEST_USER = new UserContext() {
        private UserContext delegate() {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return new HeaderUserContext(attrs.getRequest());
        }

        @Override
        public boolean isPresent() {
            return delegate().isPresent();
        }

        @Override
        public String userId() {
            return delegate().userId();
        }

        @Override
        public Role role() {
            return delegate().role();
        }
    };

    private AuthorizeInterceptor interceptor;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        interceptor = new AuthorizeInterceptor(CURRENT_REQUEST_USER);
        mvc = MockMvcBuilders.standaloneSetup(new MethodLevelController(), new ClassLevelController())
                .addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ResultActions call(String path, String role) throws Exception {
        return mvc.perform(get(path)
                .header("X-User-Id", "42")
                .header("X-User-Role", role)
                .accept(MediaType.ALL));
    }

    @Test
    void rolPermitido_laRequestProcede() throws Exception {
        call("/test/admin", "ADMINISTRADOR").andExpect(status().isOk());
    }

    @Test
    void rolDenegado_responde403ConFormatoEstandar() throws Exception {
        call("/test/admin", "CLIENTE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void elRolSeInterpretaSinImportarMayusculas() throws Exception {
        call("/test/admin", "administrador").andExpect(status().isOk());
    }

    @Test
    void hasAnyRole_aceptaCualquieraDeLosListados() throws Exception {
        call("/test/org-o-admin", "ORGANIZADOR").andExpect(status().isOk());
        call("/test/org-o-admin", "ADMINISTRADOR").andExpect(status().isOk());
        call("/test/org-o-admin", "CLIENTE").andExpect(status().isForbidden());
    }

    @Test
    void laAnotacionDeClaseAplicaALosMetodosSinAnotacion() throws Exception {
        call("/test/clase", "ADMINISTRADOR").andExpect(status().isOk());
        call("/test/clase", "CLIENTE").andExpect(status().isForbidden());
    }

    @Test
    void laAnotacionDelMetodoPrevaleceSobreLaDeLaClase() throws Exception {
        call("/test/clase-metodo", "CLIENTE").andExpect(status().isOk());              // clase exige ADMIN
        call("/test/clase-metodo", "ADMINISTRADOR").andExpect(status().isForbidden()); // el método no lo lista
    }

    @Test
    void sinAuthorize_noSeValidaNada_niSiquieraLosHeaders() throws Exception {
        mvc.perform(get("/test/libre")).andExpect(status().isOk());
    }

    @Test
    void conAuthorizeYSinHeaders_responde400() throws Exception {
        mvc.perform(get("/test/admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta el header obligatorio X-User-Id"));
    }

    @Test
    void conAuthorizeYSinRol_responde400() throws Exception {
        mvc.perform(get("/test/admin").header("X-User-Id", "42"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta el header obligatorio X-User-Role"));
    }

    @Test
    void rolDesconocidoEnElHeader_responde400() throws Exception {
        call("/test/admin", "SUPERMAN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Valores permitidos")));
    }

    @Test
    void sintaxisInvalida_responde500SinFiltrarDetalles() throws Exception {
        call("/test/sintaxis-invalida", "ADMINISTRADOR")
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("hasRole"))))
                .andExpect(content().string(not(containsString("Spel"))));
    }

    @Test
    void rolMalEscritoEnLaAnotacion_responde500() throws Exception {
        call("/test/rol-desconocido", "ADMINISTRADOR").andExpect(status().isInternalServerError());
    }

    @Test
    void expresionNoBooleana_responde500() throws Exception {
        call("/test/no-booleana", "ADMINISTRADOR").andExpect(status().isInternalServerError());
    }


    @Test
    void elContextoEsRestringido_noPermiteAccederATipos() throws Exception {
        call("/test/tipos", "ADMINISTRADOR").andExpect(status().isInternalServerError());
    }

    @Test
    void lasExpresionesSeParseanUnaSolaVez() throws Exception {
        call("/test/admin", "ADMINISTRADOR");
        call("/test/admin", "ADMINISTRADOR");
        call("/test/admin", "CLIENTE");

        assertThat(interceptor.cacheSize()).isEqualTo(1);
    }

    @RestController
    static class MethodLevelController {

        @Authorize("hasRole('ADMINISTRADOR')")
        @GetMapping("/test/admin")
        String admin() {
            return "ok";
        }

        @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
        @GetMapping("/test/org-o-admin")
        String orgOAdmin() {
            return "ok";
        }

        @GetMapping("/test/libre")
        String libre() {
            return "ok";
        }

        @Authorize("hasRole('ADMINISTRADOR'")
        @GetMapping("/test/sintaxis-invalida")
        String sintaxisInvalida() {
            return "ok";
        }

        @Authorize("hasRole('SUPERMAN')")
        @GetMapping("/test/rol-desconocido")
        String rolDesconocido() {
            return "ok";
        }

        @Authorize("'texto'")
        @GetMapping("/test/no-booleana")
        String noBooleana() {
            return "ok";
        }

        @Authorize("T(java.lang.Math).abs(1) == 1")
        @GetMapping("/test/tipos")
        String tipos() {
            return "ok";
        }
    }

    @RestController
    @Authorize("hasRole('ADMINISTRADOR')")
    static class ClassLevelController {

        @GetMapping("/test/clase")
        String heredaDeLaClase() {
            return "ok";
        }

        @Authorize("hasAnyRole('CLIENTE','ORGANIZADOR')")
        @GetMapping("/test/clase-metodo")
        String sobreescribeLaClase() {
            return "ok";
        }
    }
}