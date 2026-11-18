package org.tudai.ticketya.usuarios;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.usuarios.domain.Usuario;
import org.tudai.ticketya.usuarios.repository.UsuarioRepository;
import org.tudai.ticketya.usuarios.service.UsuarioService;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.password=ignored",
        "ticketya.logging.dir=target/test-logs",
        "ticketya.jwt.secret=" + AuthApiTest.SECRETO
})
@AutoConfigureMockMvc
@Testcontainers
class AuthApiTest {

    static final String SECRETO = "Aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository repository;

    @Autowired
    UsuarioService service;

    @Autowired
    PasswordEncoder encoder;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
    }

    @Test
    void registro_publico_creaUnClienteSinAutenticacion() throws Exception {
        mvc.perform(registro("Ana@Mail.com", "Ana", "secreta123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("ana@mail.com"))
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.roles[0]").value("CLIENTE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Usuario guardado = repository.findAll().get(0);
        assertThat(guardado.getRoles()).containsExactly(Role.CLIENTE);
        assertThat(encoder.matches("secreta123", guardado.getPasswordHash())).isTrue();
    }

    @Test
    void registro_ignoraLosRolesEnviadosPorElCliente() throws Exception {
        String cuerpo = "{\"email\":\"ana@mail.com\",\"nombre\":\"Ana\",\"password\":\"secreta123\","
                + "\"roles\":[\"ADMINISTRADOR\"]}";

        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("CLIENTE"));

        assertThat(repository.findAll().get(0).getRoles()).containsExactly(Role.CLIENTE);
    }

    @Test
    void registro_conCorreoRepetido_409() throws Exception {
        mvc.perform(registro("ana@mail.com", "Ana", "secreta123")).andExpect(status().isCreated());

        mvc.perform(registro("ANA@mail.com", "Otra", "secreta456")).andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void registro_invalido_400() throws Exception {
        mvc.perform(registro("no-es-un-correo", "Ana", "secreta123")).andExpect(status().isBadRequest());
        mvc.perform(registro("ana@mail.com", "Ana", "corta")).andExpect(status().isBadRequest());
        mvc.perform(registro("ana@mail.com", "", "secreta123")).andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void login_valido_devuelveJwtConSubRolesYExpiracion() throws Exception {
        long id = crearUsuario("ana@mail.com", "secreta123", Role.CLIENTE, Role.ORGANIZADOR);

        MvcResult resultado = mvc.perform(login("ana@mail.com", "secreta123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn();

        String token = JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.verify(new MACVerifier(SECRETO))).isTrue();

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(id));
        assertThat(claims.getStringListClaim("roles")).containsExactlyInAnyOrder("CLIENTE", "ORGANIZADOR");
        assertThat(claims.getExpirationTime().toInstant())
                .isEqualTo(claims.getIssueTime().toInstant().plus(Duration.ofHours(1)));
    }

    @Test
    void login_ignoraMayusculasYEspaciosEnElCorreo() throws Exception {
        crearUsuario("ana@mail.com", "secreta123", Role.CLIENTE);

        mvc.perform(login("  ANA@Mail.com ", "secreta123")).andExpect(status().isOk());
    }

    @Test
    void login_trasRegistroPublico_dejaRolCliente() throws Exception {
        mvc.perform(registro("ana@mail.com", "Ana", "secreta123")).andExpect(status().isCreated());

        MvcResult resultado = mvc.perform(login("ana@mail.com", "secreta123"))
                .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
        assertThat(SignedJWT.parse(token).getJWTClaimsSet().getStringListClaim("roles"))
                .containsExactly("CLIENTE");
    }

    @Test
    void login_credencialesInvalidas_401_conElMismoCuerpoSeaCualSeaElMotivo() throws Exception {
        crearUsuario("ana@mail.com", "secreta123", Role.CLIENTE);

        String passwordIncorrecta = mvc.perform(login("ana@mail.com", "incorrecta1"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String correoInexistente = mvc.perform(login("nadie@mail.com", "secreta123"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(passwordIncorrecta, "$.title"))
                .isEqualTo(JsonPath.read(correoInexistente, "$.title"));
        assertThat((String) JsonPath.read(passwordIncorrecta, "$.detail"))
                .isEqualTo(JsonPath.read(correoInexistente, "$.detail"));
    }

    @Test
    void login_sinCampos_400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private long crearUsuario(String email, String password, Role... roles) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombre("Test");
        usuario.setRoles(new HashSet<>(Set.of(roles)));
        return service.crear(usuario, password).getId();
    }

    private static MockHttpServletRequestBuilder registro(String email, String nombre, String password) {
        return post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"nombre\":\"%s\",\"password\":\"%s\"}"
                        .formatted(email, nombre, password));
    }

    private static MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }
}