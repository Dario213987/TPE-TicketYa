package org.tudai.ticketya.devoluciones.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.tudai.ticketya.contract.devoluciones.model.EstadoDevolucion;
import org.tudai.ticketya.contract.devoluciones.model.SolicitudDevolucionRequest;
import org.tudai.ticketya.devoluciones.client.EventoClient;
import org.tudai.ticketya.devoluciones.client.TicketClient;
import org.tudai.ticketya.devoluciones.client.TicketDTO;
import org.tudai.ticketya.devoluciones.domain.Devolucion;
import org.tudai.ticketya.devoluciones.repository.DevolucionRepository;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "ticketya.logging.dir=target/test-logs"
})
@AutoConfigureMockMvc
class DevolucionApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DevolucionRepository devolucionRepository;

    @MockitoBean
    private TicketClient ticketClient;

    @MockitoBean
    private EventoClient eventoClient;

    @BeforeEach
    void setUp() {
        devolucionRepository.deleteAll();
    }

    private MockHttpServletRequestBuilder postWithUser(String uri, Long userId, String role, Object body) throws Exception {
        var builder = post(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (userId != null) {
            builder.header("X-User-Id", userId);
        }
        if (role != null) {
            builder.header("X-User-Role", role);
        }
        return builder;
    }

    private MockHttpServletRequestBuilder getWithUser(String uri, Long userId, String role) {
        var builder = get(uri)
                .accept(MediaType.APPLICATION_JSON);
        if (userId != null) {
            builder.header("X-User-Id", userId);
        }
        if (role != null) {
            builder.header("X-User-Role", role);
        }
        return builder;
    }

    private Devolucion persistirDevolucion(Long clienteId, Long ticketId, Long eventoId, EstadoDevolucion estado) {
        Devolucion d = Devolucion.builder()
                .clienteId(clienteId)
                .ticketId(ticketId)
                .eventoId(eventoId)
                .fecha(Instant.now())
                .estado(estado)
                .motivo("Motivo de prueba")
                .build();
        return devolucionRepository.save(d);
    }

    private TicketDTO ticketValido(Long ticketId, Long clienteId, String estado) {
        return TicketDTO.builder()
                .id(ticketId)
                .clienteId(clienteId)
                .usuarioId(clienteId)
                .eventoId(500L)
                .precio(2000.0)
                .estado(estado)
                .utilizado(false)
                .fechaEvento(LocalDateTime.now().plusDays(5))
                .transaccionId(888L)
                .build();
    }

    @Test
    @DisplayName("POST /api/devoluciones con ticket EMITIDO propio y >= 72hs -> 201 Created")
    void solicitarDevolucion_exitoso_responde201() throws Exception {
        when(ticketClient.obtenerTicket(100L)).thenReturn(ticketValido(100L, 10L, "EMITIDO"));

        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(100L);
        req.setMotivo("No puedo asistir");

        mvc.perform(postWithUser("/api/devoluciones", 10L, "CLIENTE", req))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.clienteId").value(10))
                .andExpect(jsonPath("$.ticketId").value(100))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.transaccionId").value(888))
                .andExpect(jsonPath("$.motivo").value("No puedo asistir"));

        assertThat(devolucionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/devoluciones con menos de 72 hs para el evento -> 409 Conflict")
    void solicitarDevolucion_menosDe72Horas_responde409() throws Exception {
        TicketDTO ticket = ticketValido(100L, 10L, "EMITIDO");
        ticket.setFechaEvento(LocalDateTime.now().plusHours(24));
        when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(100L);

        mvc.perform(postWithUser("/api/devoluciones", 10L, "CLIENTE", req))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/devoluciones con ticket ya USADO -> 409 Conflict")
    void solicitarDevolucion_ticketUsado_responde409() throws Exception {
        TicketDTO ticket = ticketValido(100L, 10L, "USADO");
        when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(100L);

        mvc.perform(postWithUser("/api/devoluciones", 10L, "CLIENTE", req))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/devoluciones con ticket ya DEVUELTO -> 409 Conflict")
    void solicitarDevolucion_ticketDevuelto_responde409() throws Exception {
        TicketDTO ticket = ticketValido(100L, 10L, "DEVUELTO");
        when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(100L);

        mvc.perform(postWithUser("/api/devoluciones", 10L, "CLIENTE", req))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/devoluciones con ticket RESERVADO -> 409 Conflict")
    void solicitarDevolucion_ticketReservado_responde409() throws Exception {
        TicketDTO ticket = ticketValido(100L, 10L, "RESERVADO");
        when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(100L);

        mvc.perform(postWithUser("/api/devoluciones", 10L, "CLIENTE", req))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/devoluciones con solicitud PENDIENTE previa -> 409 Conflict")
    void solicitarDevolucion_solicitudPendientePrevia_responde409() throws Exception {
        devolucionRepository.save(Devolucion.builder()
                .ticketId(100L)
                .clienteId(10L)
                .fecha(Instant.now())
                .estado(EstadoDevolucion.PENDIENTE)
                .build());

        when(ticketClient.obtenerTicket(100L)).thenReturn(ticketValido(100L, 10L, "EMITIDO"));

        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(100L);

        mvc.perform(postWithUser("/api/devoluciones", 10L, "CLIENTE", req))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/devoluciones con ticket de otro cliente -> 403 Forbidden")
    void solicitarDevolucion_ticketAjeno_responde403() throws Exception {
        // Ticket pertenece a cliente 99, pero el usuario autenticado es 10
        when(ticketClient.obtenerTicket(100L)).thenReturn(ticketValido(100L, 99L, "EMITIDO"));

        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(100L);

        mvc.perform(postWithUser("/api/devoluciones", 10L, "CLIENTE", req))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/devoluciones con clienteId en body diferente al autenticado -> 403 Forbidden")
    void solicitarDevolucion_clienteIdDiferente_responde403() throws Exception {
        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(100L);
        req.setClienteId(99L); // usuario autenticado es 10L

        mvc.perform(postWithUser("/api/devoluciones", 10L, "CLIENTE", req))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/devoluciones con ticket inexistente -> 404 Not Found")
    void solicitarDevolucion_ticketInexistente_responde404() throws Exception {
        when(ticketClient.obtenerTicket(anyLong())).thenReturn(null);

        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(9999L);

        mvc.perform(postWithUser("/api/devoluciones", 10L, "CLIENTE", req))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/devoluciones sin autenticación -> 401 Unauthorized")
    void solicitarDevolucion_sinAutenticacion_responde401() throws Exception {
        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(100L);

        mvc.perform(postWithUser("/api/devoluciones", null, null, req))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/devoluciones con rol no CLIENTE -> 403 Forbidden")
    void solicitarDevolucion_rolOrganizador_responde403() throws Exception {
        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(100L);

        mvc.perform(postWithUser("/api/devoluciones", 10L, "ORGANIZADOR", req))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/devoluciones con motivo > 255 caracteres -> 400 Bad Request")
    void solicitarDevolucion_motivoLargo_responde400() throws Exception {
        SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
        req.setTicketId(100L);
        req.setMotivo("X".repeat(256));

        mvc.perform(postWithUser("/api/devoluciones", 10L, "CLIENTE", req))
                .andExpect(status().isBadRequest());
    }

    // ==========================================
    // Tests de Listado (TKY-66 & US-06.02)
    // ==========================================

    @Test
    @DisplayName("GET /api/devoluciones con rol ADMINISTRADOR -> 200 OK y lista todas las solicitudes")
    void listarDevoluciones_administrador_listaTodas() throws Exception {
        persistirDevolucion(10L, 101L, 1L, EstadoDevolucion.PENDIENTE);
        persistirDevolucion(20L, 102L, 1L, EstadoDevolucion.APROBADA);
        persistirDevolucion(30L, 103L, 2L, EstadoDevolucion.DENEGADA);

        mvc.perform(getWithUser("/api/devoluciones", 1L, "ADMINISTRADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    @DisplayName("GET /api/devoluciones con rol CLIENTE -> 200 OK y solo ve sus propias solicitudes")
    void listarDevoluciones_cliente_soloVeLoSuyo() throws Exception {
        persistirDevolucion(10L, 101L, 1L, EstadoDevolucion.PENDIENTE);
        persistirDevolucion(10L, 102L, 2L, EstadoDevolucion.APROBADA);
        persistirDevolucion(20L, 103L, 1L, EstadoDevolucion.PENDIENTE); // de otro cliente

        mvc.perform(getWithUser("/api/devoluciones", 10L, "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].clienteId").value(10))
                .andExpect(jsonPath("$.content[1].clienteId").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/devoluciones con rol CLIENTE pasando clienteId ajeno -> 403 Forbidden")
    void listarDevoluciones_clienteConClienteIdAjeno_responde403() throws Exception {
        persistirDevolucion(20L, 101L, 1L, EstadoDevolucion.PENDIENTE);

        mvc.perform(getWithUser("/api/devoluciones?clienteId=20", 10L, "CLIENTE"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/devoluciones con rol ORGANIZADOR -> 403 Forbidden")
    void listarDevoluciones_rolOrganizador_responde403() throws Exception {
        mvc.perform(getWithUser("/api/devoluciones", 10L, "ORGANIZADOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/devoluciones con filtro por estado -> 200 OK solo devuelve el estado solicitado")
    void listarDevoluciones_filtroPorEstado_filtraCorrectamente() throws Exception {
        persistirDevolucion(10L, 101L, 1L, EstadoDevolucion.PENDIENTE);
        persistirDevolucion(10L, 102L, 1L, EstadoDevolucion.APROBADA);
        persistirDevolucion(20L, 103L, 2L, EstadoDevolucion.PENDIENTE);

        mvc.perform(getWithUser("/api/devoluciones?estado=PENDIENTE", 1L, "ADMINISTRADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.content[1].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/devoluciones con sort inválido -> 400 Bad Request")
    void listarDevoluciones_sortInvalido_responde400() throws Exception {
        mvc.perform(getWithUser("/api/devoluciones?sort=campoInexistente,desc", 1L, "ADMINISTRADOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/devoluciones con filtro por eventoId (US-06.02) -> 200 OK filtra por evento")
    void listarDevoluciones_filtroPorEventoId_filtraCorrectamente() throws Exception {
        persistirDevolucion(10L, 101L, 42L, EstadoDevolucion.PENDIENTE);
        persistirDevolucion(10L, 102L, 99L, EstadoDevolucion.PENDIENTE);
        persistirDevolucion(20L, 103L, 42L, EstadoDevolucion.APROBADA);

        mvc.perform(getWithUser("/api/devoluciones?eventoId=42", 1L, "ADMINISTRADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].eventoId").value(42))
                .andExpect(jsonPath("$.content[1].eventoId").value(42))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }
}
