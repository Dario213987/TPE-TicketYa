package org.tudai.ticketya.devoluciones.service;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.tudai.ticketya.contract.devoluciones.model.EstadoDevolucion;
import org.tudai.ticketya.contract.devoluciones.model.RechazarDevolucionRequest;
import org.tudai.ticketya.contract.devoluciones.model.SolicitudDevolucionRequest;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.devoluciones.client.EventoClient;
import org.tudai.ticketya.devoluciones.client.EventoDTO;
import org.tudai.ticketya.devoluciones.client.TicketClient;
import org.tudai.ticketya.devoluciones.client.TicketDTO;
import org.tudai.ticketya.devoluciones.domain.Devolucion;
import org.tudai.ticketya.devoluciones.repository.DevolucionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevolucionServiceTest {

    @Mock
    private DevolucionRepository devolucionRepository;

    @Mock
    private TicketClient ticketClient;

    @Mock
    private EventoClient eventoClient;

    @Mock
    private UserContext userContext;

    @InjectMocks
    private DevolucionService devolucionService;

    private final Instant now = Instant.parse("2026-11-06T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(now, ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        devolucionService.setClock(fixedClock);
    }

    private TicketDTO crearTicketValido(String estado) {
        return TicketDTO.builder()
                .id(100L)
                .clienteId(10L)
                .usuarioId(10L)
                .eventoId(500L)
                .precio(1500.0)
                .estado(estado)
                .utilizado(false)
                .fechaEvento(LocalDateTime.now(fixedClock).plusHours(100))
                .transaccionId(999L)
                .build();
    }

    @Nested
    @DisplayName("Criterio: Ticket propio EMITIDO/VENDIDO, no utilizado, >= 72hs -> PENDIENTE")
    class CasosExitosos {

        @Test
        @DisplayName("Ticket EMITIDO con fechaEvento directa a 100 hs crea solicitud PENDIENTE")
        void ticketEmitido_conAnticipacionSuficiente_creaSolicitudPendiente() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("EMITIDO");
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);
            when(devolucionRepository.existsByTicketIdAndEstadoIn(eq(100L), anyCollection())).thenReturn(false);

            Devolucion devGuardada = Devolucion.builder()
                    .id(1L)
                    .clienteId(10L)
                    .ticketId(100L)
                    .estado(EstadoDevolucion.PENDIENTE)
                    .fecha(now)
                    .motivo("Motivo válido")
                    .transaccionId(999L)
                    .build();
            when(devolucionRepository.saveAndFlush(any(Devolucion.class))).thenReturn(devGuardada);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);
            req.setMotivo("Motivo válido");

            Devolucion resultado = devolucionService.solicitarDevolucion(req);

            assertThat(resultado).isNotNull();
            assertThat(resultado.getId()).isEqualTo(1L);
            assertThat(resultado.getEstado()).isEqualTo(EstadoDevolucion.PENDIENTE);
            assertThat(resultado.getClienteId()).isEqualTo(10L);
            assertThat(resultado.getTransaccionId()).isEqualTo(999L);
            verify(devolucionRepository).saveAndFlush(any(Devolucion.class));
        }

        @Test
        @DisplayName("Compatibilidad: Ticket con estado VENDIDO también es aceptado")
        void ticketVendido_conAnticipacionSuficiente_creaSolicitudPendiente() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("VENDIDO");
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);
            when(devolucionRepository.existsByTicketIdAndEstadoIn(eq(100L), anyCollection())).thenReturn(false);

            Devolucion devGuardada = Devolucion.builder()
                    .id(1L)
                    .clienteId(10L)
                    .ticketId(100L)
                    .estado(EstadoDevolucion.PENDIENTE)
                    .fecha(now)
                    .build();
            when(devolucionRepository.saveAndFlush(any(Devolucion.class))).thenReturn(devGuardada);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            Devolucion resultado = devolucionService.solicitarDevolucion(req);

            assertThat(resultado).isNotNull();
            assertThat(resultado.getEstado()).isEqualTo(EstadoDevolucion.PENDIENTE);
        }

        @Test
        @DisplayName("Si el ticket no trae fechaEvento, la obtiene vía EventoClient (LocalDateTime)")
        void ticketSinFechaEvento_obtieneFechaDesdeEventoClient() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("EMITIDO");
            ticket.setFechaEvento(null);
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

            EventoDTO evento = EventoDTO.builder()
                    .id(500L)
                    .nombre("Concierto Rock")
                    .fecha(LocalDateTime.now(fixedClock).plusHours(80))
                    .build();
            when(eventoClient.obtenerEvento(500L)).thenReturn(evento);
            when(devolucionRepository.existsByTicketIdAndEstadoIn(eq(100L), anyCollection())).thenReturn(false);

            Devolucion devGuardada = Devolucion.builder()
                    .id(2L)
                    .clienteId(10L)
                    .ticketId(100L)
                    .estado(EstadoDevolucion.PENDIENTE)
                    .fecha(now)
                    .build();
            when(devolucionRepository.saveAndFlush(any(Devolucion.class))).thenReturn(devGuardada);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            Devolucion resultado = devolucionService.solicitarDevolucion(req);

            assertThat(resultado).isNotNull();
            assertThat(resultado.getId()).isEqualTo(2L);
            verify(eventoClient).obtenerEvento(500L);
        }

        @Test
        @DisplayName("Permite solicitar de nuevo si una solicitud anterior fue DENEGADA y sigue en plazo")
        void solicitudPreviaDenegada_permiteNuevaSolicitud() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("EMITIDO");
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);
            // No hay solicitud PENDIENTE (la anterior fue DENEGADA)
            when(devolucionRepository.existsByTicketIdAndEstadoIn(eq(100L), anyCollection())).thenReturn(false);

            Devolucion nuevaDev = Devolucion.builder()
                    .id(3L)
                    .clienteId(10L)
                    .ticketId(100L)
                    .estado(EstadoDevolucion.PENDIENTE)
                    .fecha(now)
                    .build();
            when(devolucionRepository.saveAndFlush(any(Devolucion.class))).thenReturn(nuevaDev);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            Devolucion resultado = devolucionService.solicitarDevolucion(req);

            assertThat(resultado).isNotNull();
            assertThat(resultado.getId()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("Criterio: Borde exacto de 72 horas y menos de 72 hs -> 409")
    class Plazo72Horas {

        @Test
        @DisplayName("Borde exacto: Faltando exactamente 72 horas (inclusivo) se acepta")
        void bordeExacto72Horas_esAceptado() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("EMITIDO");
            // Exactamente 72 horas después de now
            ticket.setFechaEvento(LocalDateTime.now(fixedClock).plusHours(72));
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);
            when(devolucionRepository.existsByTicketIdAndEstadoIn(eq(100L), anyCollection())).thenReturn(false);

            Devolucion devGuardada = Devolucion.builder()
                    .id(1L)
                    .clienteId(10L)
                    .ticketId(100L)
                    .estado(EstadoDevolucion.PENDIENTE)
                    .fecha(now)
                    .build();
            when(devolucionRepository.saveAndFlush(any(Devolucion.class))).thenReturn(devGuardada);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            Devolucion resultado = devolucionService.solicitarDevolucion(req);

            assertThat(resultado).isNotNull();
        }

        @Test
        @DisplayName("Faltando 71 horas 59 minutos (menos de 72 hs) responde 409")
        void menosDe72Horas_lanzaConflict() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("EMITIDO");
            ticket.setFechaEvento(LocalDateTime.now(fixedClock).plusHours(72).minusMinutes(1));
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("72 horas");
        }

        @Test
        @DisplayName("Si la fecha del evento no se puede determinar responde 409")
        void fechaEventoNula_lanzaConflict() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("EMITIDO");
            ticket.setFechaEvento(null);
            ticket.setEventoId(null);
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("No es posible verificar el plazo del evento");
        }
    }

    @Nested
    @DisplayName("Criterio: Ticket usado, devuelto, o estados inválidos -> 409")
    class EstadosTicketInvalidos {

        @Test
        @DisplayName("Ticket con utilizado=true responde 409")
        void ticketUtilizado_lanzaConflict() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("EMITIDO");
            ticket.setUtilizado(true);
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("ya ha sido utilizado");
        }

        @Test
        @DisplayName("Ticket con estado USADO responde 409")
        void ticketEstadoUsado_lanzaConflict() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("USADO");
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("ya ha sido utilizado");
        }

        @Test
        @DisplayName("Ticket con estado DEVUELTO responde 409")
        void ticketEstadoDevuelto_lanzaConflict() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("DEVUELTO");
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("ya fue devuelto");
        }

        @Test
        @DisplayName("Ticket con estado RESERVADO responde 409")
        void ticketEstadoReservado_lanzaConflict() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("RESERVADO");
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("reservado");
        }

        @Test
        @DisplayName("Ticket con estado CANCELADO responde 409")
        void ticketEstadoCancelado_lanzaConflict() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("CANCELADO");
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("cancelado");
        }
    }

    @Nested
    @DisplayName("Criterio: Solicitud pendiente duplicada -> 409")
    class SolicitudPendienteDuplicada {

        @Test
        @DisplayName("Solicitud ya pendiente detectada por repositorio responde 409")
        void solicitudYaPendienteEnRepo_lanzaConflict() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("EMITIDO");
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);
            when(devolucionRepository.existsByTicketIdAndEstadoIn(eq(100L), anyCollection())).thenReturn(true);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("solicitud de devolución");
        }

        @Test
        @DisplayName("Violación de unicidad por concurrencia (DataIntegrityViolationException) responde 409")
        void concurrenciaViolacionUnica_lanzaConflict() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("EMITIDO");
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);
            when(devolucionRepository.existsByTicketIdAndEstadoIn(eq(100L), anyCollection())).thenReturn(false);
            when(devolucionRepository.saveAndFlush(any(Devolucion.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint uq_devoluciones_ticket_pendiente"));

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("solicitud de devolución pendiente");
        }
    }

    @Nested
    @DisplayName("Criterio: Ticket de otro cliente o usuario no autorizado -> 403")
    class PermisosYTitularidad {

        @Test
        @DisplayName("Ticket de otro cliente responde 403")
        void ticketDeOtroCliente_lanzaForbidden() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("EMITIDO");
            ticket.setUsuarioId(99L);
            ticket.setClienteId(99L);
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("no pertenece al cliente solicitante");
        }

        @Test
        @DisplayName("Request con clienteId explícito distinto al autenticado responde 403")
        void clienteIdEnRequestDistintoAlAutenticado_lanzaForbidden() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);
            req.setClienteId(20L); // diferente a 10L

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("no coincide con el usuario autenticado");
        }

        @Test
        @DisplayName("Ticket sin titular asociado responde 403")
        void ticketSinTitular_lanzaForbidden() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            TicketDTO ticket = crearTicketValido("EMITIDO");
            ticket.setUsuarioId(null);
            ticket.setClienteId(null);
            when(ticketClient.obtenerTicket(100L)).thenReturn(ticket);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("no incluye un cliente asociado");
        }
    }

    @Nested
    @DisplayName("Criterio: Ticket inexistente -> 404")
    class InexistenciaTicket {

        @Test
        @DisplayName("Ticket inexistente (retorna null) responde 404")
        void ticketInexistenteNull_lanzaNotFound() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);
            when(ticketClient.obtenerTicket(100L)).thenReturn(null);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("no fue encontrado");
        }

        @Test
        @DisplayName("Ticket inexistente (FeignException.NotFound) responde 404")
        void ticketInexistenteFeignNotFound_lanzaNotFound() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            Request feignRequest = Request.create(Request.HttpMethod.GET, "/api/tickets/100",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(ticketClient.obtenerTicket(100L)).thenThrow(new FeignException.NotFound("Not Found", feignRequest, null, null));

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("no fue encontrado");
        }
    }

    @Nested
    @DisplayName("Criterio: Longitud de motivo (maxLength 255) -> 400")
    class ValidacionMotivo {

        @Test
        @DisplayName("Motivo que supera los 255 caracteres responde 400")
        void motivoDemasiadoLargo_lanzaBadRequest() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.userId()).thenReturn(10L);

            SolicitudDevolucionRequest req = new SolicitudDevolucionRequest();
            req.setTicketId(100L);
            req.setMotivo("A".repeat(256));

            assertThatThrownBy(() -> devolucionService.solicitarDevolucion(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("255 caracteres");
        }
    }

    @Nested
    @DisplayName("Criterio: Rechazar devolución (US-06.04 / TKY-68)")
    class RechazarDevolucionTests {

        @Test
        @DisplayName("Rechazo exitoso pasa a DENEGADA, guarda motivoRechazo y preserva motivo del cliente")
        void rechazoExitoso_guardaMotivoRechazoYDatosAuditoria() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
            when(userContext.userId()).thenReturn(999L);

            Devolucion dev = Devolucion.builder()
                    .id(1L)
                    .clienteId(10L)
                    .ticketId(100L)
                    .estado(EstadoDevolucion.PENDIENTE)
                    .motivo("Motivo del cliente")
                    .build();

            when(devolucionRepository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(dev));
            when(devolucionRepository.saveAndFlush(any(Devolucion.class))).thenAnswer(i -> i.getArgument(0));

            RechazarDevolucionRequest req = new RechazarDevolucionRequest();
            req.setMotivo("Entrada duplicada en sistema");

            Devolucion res = devolucionService.rechazarDevolucion(1L, req);

            assertThat(res.getEstado()).isEqualTo(EstadoDevolucion.DENEGADA);
            assertThat(res.getMotivo()).isEqualTo("Motivo del cliente");
            assertThat(res.getMotivoRechazo()).isEqualTo("Entrada duplicada en sistema");
            assertThat(res.getResueltaPor()).isEqualTo(999L);
            assertThat(res.getFechaResolucion()).isNotNull();
        }

        @Test
        @DisplayName("Rechazo con motivo vacío o en blanco responde 400")
        void rechazoMotivoVacio_lanzaBadRequest() {
            RechazarDevolucionRequest reqNull = new RechazarDevolucionRequest();
            assertThatThrownBy(() -> devolucionService.rechazarDevolucion(1L, reqNull))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("El motivo de rechazo es obligatorio");

            RechazarDevolucionRequest reqBlank = new RechazarDevolucionRequest();
            reqBlank.setMotivo("   ");
            assertThatThrownBy(() -> devolucionService.rechazarDevolucion(1L, reqBlank))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("El motivo de rechazo es obligatorio");
        }

        @Test
        @DisplayName("Rechazo con motivo > 255 caracteres responde 400")
        void rechazoMotivoMayor255_lanzaBadRequest() {
            RechazarDevolucionRequest req = new RechazarDevolucionRequest();
            req.setMotivo("A".repeat(256));

            assertThatThrownBy(() -> devolucionService.rechazarDevolucion(1L, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("255 caracteres");
        }

        @Test
        @DisplayName("Rechazo por usuario no administrador o sin autenticar responde 403")
        void rechazoUsuarioNoAdmin_lanzaForbidden() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.role()).thenReturn(Role.CLIENTE);

            RechazarDevolucionRequest req = new RechazarDevolucionRequest();
            req.setMotivo("Motivo válido");

            assertThatThrownBy(() -> devolucionService.rechazarDevolucion(1L, req))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("Rechazo de devolución inexistente responde 404")
        void rechazoDevolucionInexistente_lanzaNotFound() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
            when(devolucionRepository.findByIdForUpdate(99L)).thenReturn(java.util.Optional.empty());

            RechazarDevolucionRequest req = new RechazarDevolucionRequest();
            req.setMotivo("Motivo válido");

            assertThatThrownBy(() -> devolucionService.rechazarDevolucion(99L, req))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("no existe");
        }

        @Test
        @DisplayName("Rechazo de devolución ya resuelta responde 409")
        void rechazoDevolucionYaResuelta_lanzaConflict() {
            when(userContext.isPresent()).thenReturn(true);
            when(userContext.role()).thenReturn(Role.ADMINISTRADOR);

            Devolucion dev = Devolucion.builder()
                    .id(1L)
                    .estado(EstadoDevolucion.DENEGADA)
                    .build();

            when(devolucionRepository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(dev));

            RechazarDevolucionRequest req = new RechazarDevolucionRequest();
            req.setMotivo("Motivo válido");

            assertThatThrownBy(() -> devolucionService.rechazarDevolucion(1L, req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("ya fue resuelta");
        }
    }
}
