package org.tudai.ticketya.web.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.tudai.ticketya.core.HeaderNames;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.exception.InvalidUserContextException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeaderUserContextTest {

    private MockHttpServletRequest request;
    private HeaderUserContext ctx;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        ctx = new HeaderUserContext(request);
    }

    @Test
    void conAmbosHeaders_devuelveUsuarioYRol() {
        request.addHeader(HeaderNames.USER_ID, "42");
        request.addHeader(HeaderNames.USER_ROLE, "organizador");

        assertThat(ctx.isPresent()).isTrue();
        assertThat(ctx.userId()).isEqualTo("42");
        assertThat(ctx.role()).isEqualTo(Role.ORGANIZADOR);
    }

    @Test
    void sinHeaders_noEstaPresenteYFallaAlPedirlos() {
        assertThat(ctx.isPresent()).isFalse();
        assertThatThrownBy(ctx::userId).isInstanceOf(InvalidUserContextException.class)
                .hasMessageContaining(HeaderNames.USER_ID);
        assertThatThrownBy(ctx::role).isInstanceOf(InvalidUserContextException.class)
                .hasMessageContaining(HeaderNames.USER_ROLE);
    }

    @Test
    void headerEnBlanco_seTrataComoAusente() {
        request.addHeader(HeaderNames.USER_ID, "   ");

        assertThat(ctx.isPresent()).isFalse();
        assertThatThrownBy(ctx::userId).isInstanceOf(InvalidUserContextException.class);
    }

    @Test
    void rolDesconocido_fallaListandoLosPermitidos() {
        request.addHeader(HeaderNames.USER_ROLE, "SUPERMAN");

        assertThatThrownBy(ctx::role).isInstanceOf(InvalidUserContextException.class)
                .hasMessageContaining("ADMINISTRADOR");
    }
}