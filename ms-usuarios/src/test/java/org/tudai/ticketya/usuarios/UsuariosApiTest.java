package org.tudai.ticketya.usuarios;

import com.jayway.jsonpath.JsonPath;
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

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class UsuariosApiTest {

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
    void elAdminCreaUnUsuarioConVariosRoles_yLaContraseniaQuedaHasheada() throws Exception {
        mvc.perform(crear("ana@mail.com", "Ana", "secreta123", "CLIENTE", "ORGANIZADOR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("ana@mail.com"))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("CLIENTE", "ORGANIZADOR")))
                .andExpect(jsonPath("$.creadaEn").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Usuario guardado = repository.findAll().get(0);
        assertThat(guardado.getPasswordHash()).startsWith("$2").isNotEqualTo("secreta123");
        assertThat(encoder.matches("secreta123", guardado.getPasswordHash())).isTrue();
        assertThat(guardado.getRoles()).containsExactlyInAnyOrder(Role.CLIENTE, Role.ORGANIZADOR);
        assertThat(guardado.getCreadoEl()).isNotNull();
    }

    @Test
    void elCorreoSeGuardaEnMinusculas() throws Exception {
        mvc.perform(crear("Ana@Mail.COM", "Ana", "secreta123", "CLIENTE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@mail.com"));
    }

    @Test
    void unCorreoRepetido_409_sinDistinguirMayusculas() throws Exception {
        mvc.perform(crear("ana@mail.com", "Ana", "secreta123", "CLIENTE")).andExpect(status().isCreated());

        mvc.perform(crear("ana@mail.com", "Otra", "secreta456", "CLIENTE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un usuario con ese correo"));
        mvc.perform(crear("ANA@MAIL.COM", "Otra", "secreta456", "CLIENTE")).andExpect(status().isConflict());

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void solicitudInvalida_400() throws Exception {
        mvc.perform(crear("no-es-un-correo", "Ana", "secreta123", "CLIENTE")).andExpect(status().isBadRequest());
        mvc.perform(crear("ana@mail.com", "Ana", "corta", "CLIENTE")).andExpect(status().isBadRequest());
        mvc.perform(crear("ana@mail.com", "Ana", "secreta123")).andExpect(status().isBadRequest());               // sin roles
        mvc.perform(crear("ana@mail.com", "Ana", "secreta123", "SUPERMAN")).andExpect(status().isBadRequest());   // rol inexistente
        mvc.perform(crear("ana@mail.com", "", "secreta123", "CLIENTE")).andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void soloUnAdministradorAccede() throws Exception {
        mvc.perform(como(post("/api/usuarios"), "5", "ORGANIZADOR")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("a@mail.com", "A", "secreta123", "CLIENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(como(get("/api/usuarios"), "7", "CLIENTE")).andExpect(status().isForbidden());
        mvc.perform(como(get("/api/usuarios/1"), "7", "CLIENTE")).andExpect(status().isForbidden());
        mvc.perform(como(put("/api/usuarios/1/roles"), "7", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMINISTRADOR\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        assertThat(repository.count()).isZero();
    }

    @Test
    void obtenerPorId() throws Exception {
        long id = idDe(mvc.perform(crear("ana@mail.com", "Ana", "secreta123", "CLIENTE")).andReturn());

        mvc.perform(admin(get("/api/usuarios/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@mail.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(admin(get("/api/usuarios/999999"))).andExpect(status().isNotFound());
    }

    @Test
    void listar_conPaginadoYOrden() throws Exception {
        mvc.perform(crear("b@mail.com", "B", "secreta123", "CLIENTE")).andExpect(status().isCreated());
        mvc.perform(crear("a@mail.com", "A", "secreta123", "CLIENTE")).andExpect(status().isCreated());
        mvc.perform(crear("c@mail.com", "C", "secreta123", "CLIENTE")).andExpect(status().isCreated());

        mvc.perform(admin(get("/api/usuarios?size=2&sort=email,asc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].email").value("a@mail.com"))
                .andExpect(jsonPath("$.content[1].email").value("b@mail.com"));
        mvc.perform(admin(get("/api/usuarios?size=2&sort=email,asc&page=1")))
                .andExpect(jsonPath("$.content[0].email").value("c@mail.com"));

        mvc.perform(admin(get("/api/usuarios?sort=passwordHash,asc"))).andExpect(status().isBadRequest());
    }

    @Test
    void cambiarRoles_reemplazaLosAnteriores() throws Exception {
        long id = idDe(mvc.perform(crear("ana@mail.com", "Ana", "secreta123", "CLIENTE")).andReturn());

        mvc.perform(admin(put("/api/usuarios/" + id + "/roles"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ORGANIZADOR\",\"PERSONAL_ACCESO\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ORGANIZADOR", "PERSONAL_ACCESO")));

        assertThat(repository.findById(id).orElseThrow().getRoles())
                .containsExactlyInAnyOrder(Role.ORGANIZADOR, Role.PERSONAL_ACCESO);
    }

    @Test
    void cambiarRoles_invalido() throws Exception {
        long id = idDe(mvc.perform(crear("ana@mail.com", "Ana", "secreta123", "CLIENTE")).andReturn());

        mvc.perform(admin(put("/api/usuarios/" + id + "/roles"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(admin(put("/api/usuarios/999999/roles"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"CLIENTE\"]}"))
                .andExpect(status().isNotFound());
        assertThat(repository.findById(id).orElseThrow().getRoles()).containsExactly(Role.CLIENTE);
    }

    @Test
    void administradorInicial_seCreaUnaSolaVez() {
        service.crearAdministradorSiNoExiste("admin@ticketya.com", "claveSegura1");
        service.crearAdministradorSiNoExiste("ADMIN@ticketya.com", "otraClave99");

        assertThat(repository.count()).isEqualTo(1);
        Usuario admin = repository.findAll().get(0);
        assertThat(admin.getRoles()).containsExactly(Role.ADMINISTRADOR);
        assertThat(encoder.matches("claveSegura1", admin.getPasswordHash())).isTrue();
    }


    private static String cuerpo(String email, String nombre, String password, String... roles) {
        String lista = Arrays.stream(roles).map(r -> "\"" + r + "\"").collect(Collectors.joining(","));
        return "{\"email\":\"%s\",\"nombre\":\"%s\",\"password\":\"%s\",\"roles\":[%s]}"
                .formatted(email, nombre, password, lista);
    }

    private MockHttpServletRequestBuilder crear(String email, String nombre, String password, String... roles) {
        return admin(post("/api/usuarios"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(email, nombre, password, roles));
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return como(request, "9", "ADMINISTRADOR");
    }

    private static MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder request, String userId, String rol) {
        return request.header("X-User-Id", userId).header("X-User-Role", rol);
    }

    private static long idDe(MvcResult resultado) throws Exception {
        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }
}