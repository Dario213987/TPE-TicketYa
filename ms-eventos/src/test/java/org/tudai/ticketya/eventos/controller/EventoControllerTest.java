package org.tudai.ticketya.eventos.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.EventoBorradorCreateRequest;
import org.tudai.ticketya.contract.eventos.model.EventoEstadoResponse;
import org.tudai.ticketya.contract.eventos.model.EventoResponse;
import org.tudai.ticketya.contract.eventos.model.PrecioEventoSectorResponse;
import org.tudai.ticketya.contract.eventos.model.TipoEvento;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.exception.SectoresSinPrecioException;
import org.tudai.ticketya.eventos.service.EventoService;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;
import org.tudai.ticketya.web.security.AuthorizeInterceptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventoControllerTest {

    private EventoService eventoService;
    private UserContext userContext;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        eventoService = mock(EventoService.class);
        userContext = mock(UserContext.class);

        mvc = mockMvc(new GlobalExceptionHandler());
    }

    @Test
    void listarEventos_devuelveLaPaginaDelServicio() throws Exception {
        Evento evento = Evento.builder()
                .id(1L)
                .nombre("Festival de Verano")
                .tipo(TipoEvento.FESTIVAL)
                .fecha(LocalDateTime.of(2026, 12, 1, 20, 0))
                .estado(EstadoEvento.PUBLICADO)
                .build();
        Pageable pageable = PageRequest.of(0, 20, Sort.by("fecha"));
        when(eventoService.listarPublicados(pageable))
                .thenReturn(new PageImpl<>(List.of(evento), pageable, 1));

        mvc.perform(get("/api/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nombre").value("Festival de Verano"))
                .andExpect(jsonPath("$.content[0].tipo").value("FESTIVAL"))
                .andExpect(jsonPath("$.content[0].estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void listarEventos_conSortInvalido_devuelve400() throws Exception {
        mvc.perform(get("/api/eventos").param("sort", "precio,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud inválida"));
    }

    @Test
    void crearEventoBorrador_devuelve201ConElOrganizadorYEstado() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.crearEventoBorrador(any(EventoBorradorCreateRequest.class), eq(7L)))
                .thenReturn(new EventoResponse(1L, "Recital", TipoEvento.RECITAL,
                        LocalDateTime.now().plusDays(10), EstadoEvento.BORRADOR, 7L));

        mvc.perform(post("/api/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Recital",
                                  "descripcion": "Fecha de prueba",
                                  "tipo": "RECITAL",
                                  "fecha": "%s",
                                  "lugarId": 10
                                }
                                """.formatted(LocalDateTime.now().plusDays(10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.organizadorId").value(7));
    }

    @Test
    void definirPrecioSector_devuelve200() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.definirPrecioSector(eq(1L), eq(2L), eq(15000.0), eq(7L), eq(false)))
                .thenReturn(new PrecioEventoSectorResponse(1L, 2L, 15000.0));

        mvc.perform(put("/api/eventos/1/sectores/2/precio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "precio": 15000.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(1))
                .andExpect(jsonPath("$.sectorId").value(2))
                .andExpect(jsonPath("$.precio").value(15000.0));
    }

    @Test
    void definirPrecioSector_conPrecioNoPositivo_devuelve400() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);

        mvc.perform(put("/api/eventos/1/sectores/2/precio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "precio": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors[?(@.field=='precio')]").exists());
    }

    @Test
    void definirPrecioSector_conSectorAjenoAlLugar_devuelve400() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.definirPrecioSector(eq(1L), eq(2L), any(Double.class), eq(7L), eq(false)))
                .thenThrow(new BadRequestException("El sector 2 no pertenece al lugar del evento"));

        mvc.perform(put("/api/eventos/1/sectores/2/precio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "precio": 15000.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El sector 2 no pertenece al lugar del evento"));
    }

    @Test
    void definirPrecioSector_enEventoPublicado_devuelve409() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.definirPrecioSector(eq(1L), eq(2L), any(Double.class), eq(7L), eq(false)))
                .thenThrow(new ConflictException("Solo se pueden definir precios en eventos en estado BORRADOR"));

        mvc.perform(put("/api/eventos/1/sectores/2/precio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "precio": 15000.0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Solo se pueden definir precios en eventos en estado BORRADOR"));
    }

    @Test
    void definirPrecioSector_enEventoDeOtroOrganizador_devuelve403() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.definirPrecioSector(eq(1L), eq(2L), any(Double.class), eq(7L), eq(false)))
                .thenThrow(new ForbiddenException("No podés definir precios en un evento que no te pertenece"));

        mvc.perform(put("/api/eventos/1/sectores/2/precio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "precio": 15000.0
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value("No podés definir precios en un evento que no te pertenece"));
    }

    @Test
    void actualizarEvento_devuelveElEventoActualizado() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.actualizarEvento(eq(1L), any(), eq(7L), eq(false)))
                .thenReturn(new EventoResponse().id(1L).nombre("Nombre nuevo").estado(EstadoEvento.BORRADOR));

        mvc.perform(put("/api/eventos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Nombre nuevo"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Nombre nuevo"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    @Test
    void actualizarEvento_publicadoConCambioEstructural_devuelve409() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.actualizarEvento(eq(1L), any(), eq(7L), eq(false)))
                .thenThrow(new ConflictException(
                        "No se puede modificar la fecha de un evento ya publicado. Cancelalo o reprogramalo."));

        mvc.perform(put("/api/eventos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fecha": "2026-12-15T20:00:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("No se puede modificar la fecha de un evento ya publicado. Cancelalo o reprogramalo."));
    }

    @Test
    void eliminarEvento_borrador_devuelve204() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);

        mvc.perform(delete("/api/eventos/1"))
                .andExpect(status().isNoContent());

        verify(eventoService).eliminarEvento(1L, 7L, false);
    }

    @Test
    void eliminarEvento_publicado_devuelve409() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        org.mockito.Mockito.doThrow(new ConflictException(
                        "Solo se pueden eliminar eventos en estado BORRADOR"))
                .when(eventoService).eliminarEvento(1L, 7L, false);

        mvc.perform(delete("/api/eventos/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Solo se pueden eliminar eventos en estado BORRADOR"));
    }

    @Test
    void publicarEvento_comoOrganizador_devuelve200() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, false))
                .thenReturn(eventoPublicado());

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Festival"))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));

        verify(eventoService).publicarEvento(1L, 7L, false);
    }

    @Test
    void publicarEvento_comoAdministrador_invocaServicioComoAdmin() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, true))
                .thenReturn(eventoPublicado());

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));

        verify(eventoService).publicarEvento(1L, 7L, true);
    }

    @Test
    void publicarEvento_comoCliente_devuelve403SinLlamarAlServicio() throws Exception {
        when(userContext.role()).thenReturn(Role.CLIENTE);
        when(userContext.userId()).thenReturn(7L);

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventoService);
    }

    @Test
    void publicarEvento_conSectoresSinPrecio_devuelve409ConLosSectores() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, false))
                .thenThrow(new SectoresSinPrecioException(List.of(
                        new SectoresSinPrecioException.SectorFaltante(10L, "Platea"),
                        new SectoresSinPrecioException.SectorFaltante(20L, "Campo"))));

        MockMvc eventosMvc = mockMvc(new org.tudai.ticketya.eventos.config.EventosExceptionHandler());

        eventosMvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("No se puede publicar el evento"))
                .andExpect(jsonPath("$.detail").value("Los siguientes sectores no tienen precio: Platea, Campo"))
                .andExpect(jsonPath("$.sectores[0].id").value(10))
                .andExpect(jsonPath("$.sectores[1].id").value(20));

        verify(eventoService).publicarEvento(1L, 7L, false);
    }

    @Test
    void publicarEvento_conEventoInexistente_devuelve404() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, false))
                .thenThrow(new NotFoundException("Evento 1 no existe"));

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Evento 1 no existe"));
    }

    @Test
    void publicarEvento_conEventoInvalido_devuelve400() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, false))
                .thenThrow(new BadRequestException("La fecha del evento no es valida"));

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha del evento no es valida"));
    }

    @Test
    void publicarEvento_conMsTicketsNoDisponible_devuelve502() throws Exception {
        when(userContext.role()).thenReturn(Role.ORGANIZADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.publicarEvento(1L, 7L, false))
                .thenThrow(new RemoteServiceException(502, "ms-tickets no disponible"));

        mvc.perform(put("/api/eventos/1/publicar"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Error en servicio dependiente"));
    }

    @Test
    void cancelarEvento_comoAdministrador_devuelve202() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.cancelarEvento(1L))
                .thenReturn(eventoCancelado());

        mvc.perform(post("/api/eventos/1/cancelar"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Festival"))
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        verify(eventoService).cancelarEvento(1L);
    }

    @Test
    void cancelarEvento_conRolNoAdministrador_devuelve403SinLlamarAlServicio() throws Exception {
        when(userContext.userId()).thenReturn(7L);

        for (Role rol : List.of(Role.ORGANIZADOR, Role.CLIENTE, Role.PERSONAL_ACCESO)) {
            when(userContext.role()).thenReturn(rol);

            mvc.perform(post("/api/eventos/1/cancelar"))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(eventoService);
    }

    @Test
    void cancelarEvento_conEventoInexistente_devuelve404() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.cancelarEvento(99L))
                .thenThrow(new NotFoundException("No se encontró el evento solicitado"));

        mvc.perform(post("/api/eventos/99/cancelar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No se encontró el evento solicitado"));
    }

    @Test
    void cancelarEvento_enEstadoNoCancelable_devuelve409() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.cancelarEvento(1L))
                .thenThrow(new ConflictException(
                        "El evento debe estar publicado, suspendido o agotado para poder ser cancelado"));

        mvc.perform(post("/api/eventos/1/cancelar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("El evento debe estar publicado, suspendido o agotado para poder ser cancelado"));
    }

    @Test
    void cancelarEvento_conMsTicketsNoDisponible_devuelve502() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.cancelarEvento(1L))
                .thenThrow(new RemoteServiceException(502, "ms-tickets no disponible"));

        mvc.perform(post("/api/eventos/1/cancelar"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Error en servicio dependiente"));
    }

    @Test
    void suspenderEvento_comoAdministrador_devuelve200() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.suspenderEvento(1L))
                .thenReturn(eventoSuspendido());

        mvc.perform(put("/api/eventos/1/suspender"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Festival"))
                .andExpect(jsonPath("$.estado").value("SUSPENDIDO"));

        verify(eventoService).suspenderEvento(1L);
    }

    @Test
    void suspenderEvento_conRolNoAdministrador_devuelve403SinLlamarAlServicio() throws Exception {
        when(userContext.userId()).thenReturn(7L);

        for (Role rol : List.of(Role.ORGANIZADOR, Role.CLIENTE, Role.PERSONAL_ACCESO)) {
            when(userContext.role()).thenReturn(rol);

            mvc.perform(put("/api/eventos/1/suspender"))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(eventoService);
    }

    @Test
    void suspenderEvento_conEventoInexistente_devuelve404() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.suspenderEvento(99L))
                .thenThrow(new NotFoundException("No se encontró el evento solicitado"));

        mvc.perform(put("/api/eventos/99/suspender"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No se encontró el evento solicitado"));
    }

    @Test
    void suspenderEvento_enEstadoNoSuspendible_devuelve409() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.suspenderEvento(1L))
                .thenThrow(new ConflictException("El evento debe ser publicado para poder ser suspendido"));

        mvc.perform(put("/api/eventos/1/suspender"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("El evento debe ser publicado para poder ser suspendido"));
    }

    @Test
    void suspenderEvento_conMsTicketsNoDisponible_devuelve502() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.suspenderEvento(1L))
                .thenThrow(new RemoteServiceException(502, "ms-tickets no disponible"));

        mvc.perform(put("/api/eventos/1/suspender"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Error en servicio dependiente"));
    }

    @Test
    void reactivarEvento_comoAdministrador_devuelve200() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.reactivarEvento(1L))
                .thenReturn(eventoPublicado());

        mvc.perform(put("/api/eventos/1/reactivar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Festival"))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));

        verify(eventoService).reactivarEvento(1L);
    }

    @Test
    void reactivarEvento_conRolNoAdministrador_devuelve403SinLlamarAlServicio() throws Exception {
        when(userContext.userId()).thenReturn(7L);

        for (Role rol : List.of(Role.ORGANIZADOR, Role.CLIENTE, Role.PERSONAL_ACCESO)) {
            when(userContext.role()).thenReturn(rol);

            mvc.perform(put("/api/eventos/1/reactivar"))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(eventoService);
    }

    @Test
    void reactivarEvento_conEventoInexistente_devuelve404() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.reactivarEvento(99L))
                .thenThrow(new NotFoundException("No se encontró el evento solicitado"));

        mvc.perform(put("/api/eventos/99/reactivar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No se encontró el evento solicitado"));
    }

    @Test
    void reactivarEvento_enEstadoNoSuspendido_devuelve409() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.reactivarEvento(1L))
                .thenThrow(new ConflictException("El evento debe estar suspendido para poder ser reactivado"));

        mvc.perform(put("/api/eventos/1/reactivar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("El evento debe estar suspendido para poder ser reactivado"));
    }

    @Test
    void reactivarEvento_conMsTicketsNoDisponible_devuelve502() throws Exception {
        when(userContext.role()).thenReturn(Role.ADMINISTRADOR);
        when(userContext.userId()).thenReturn(7L);
        when(eventoService.reactivarEvento(1L))
                .thenThrow(new RemoteServiceException(502, "ms-tickets no disponible"));

        mvc.perform(put("/api/eventos/1/reactivar"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Error en servicio dependiente"));
    }

    private MockMvc mockMvc(Object... controllerAdvice) {
        return MockMvcBuilders.standaloneSetup(new EventoController(eventoService, new ModelMapper(), userContext))
                .setControllerAdvice(controllerAdvice)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .addInterceptors(new AuthorizeInterceptor(userContext))
                .build();
    }

    private EventoEstadoResponse eventoPublicado() {
        return new EventoEstadoResponse(1L, "Festival", EstadoEvento.PUBLICADO);
    }

    private EventoEstadoResponse eventoCancelado() {
        return new EventoEstadoResponse(1L, "Festival", EstadoEvento.CANCELADO);
    }

    private EventoEstadoResponse eventoSuspendido() {
        return new EventoEstadoResponse(1L, "Festival", EstadoEvento.SUSPENDIDO);
    }
}
