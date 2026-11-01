package org.tudai.ticketya.web.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.tudai.ticketya.core.RequestLogger;
import org.tudai.ticketya.core.UserContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestLoggingFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogger.LOGGER_NAME);
    private final UserContext userContext = mock(UserContext.class);
    private final RequestLoggingFilter filter = new RequestLoggingFilter(userContext, new String[]{"/actuator/**"});
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/eventos/7");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attach() {
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void registraUnaLineaConMetodoRutaUsuarioStatusYDuracion() throws Exception {
        when(userContext.isPresent()).thenReturn(true);
        when(userContext.userId()).thenReturn("42");

        filter.doFilter(request, response, (rq, rs) -> ((HttpServletResponse) rs).setStatus(200));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).startsWith("GET /api/eventos/7 -> 200 (").endsWith("user=42");
        assertThat(event.getMDCPropertyMap()).containsEntry("userId", "42").containsEntry("status", "200");
        assertThat(Long.parseLong(event.getMDCPropertyMap().get("durationMs"))).isGreaterThanOrEqualTo(0);
    }

    @Test
    void sinUsuario_seRegistraComoAnonymous() throws Exception {
        filter.doFilter(request, response, (rq, rs) -> { });

        assertThat(appender.list.get(0).getFormattedMessage()).endsWith("user=anonymous");
    }

    @Test
    void siElUserContextFalla_seRegistraComoAnonymous() throws Exception {
        when(userContext.isPresent()).thenThrow(new IllegalStateException("sin request"));

        filter.doFilter(request, response, (rq, rs) -> { });

        assertThat(appender.list.get(0).getFormattedMessage()).endsWith("user=anonymous");
    }

    @Test
    void registraElStatusQueDejoElControllerYElNivelSegunElStatus() throws Exception {
        filter.doFilter(request, response, (rq, rs) -> ((HttpServletResponse) rs).setStatus(404));
        filter.doFilter(request, new MockHttpServletResponse(), (rq, rs) -> ((HttpServletResponse) rs).setStatus(503));

        assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("status", "404");
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(1).getMDCPropertyMap()).containsEntry("status", "503");
        assertThat(appender.list.get(1).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void siLaExcepcionLlegaAlFiltro_registraStatus500YLaRelanza() {
        assertThatThrownBy(() -> filter.doFilter(request, response, (rq, rs) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("status", "500");
    }

    @Test
    void noIncluyeQueryStringNiHeadersNiBody() throws Exception {
        request.setQueryString("token=secreto123&password=clave456");
        request.addHeader("Authorization", "Bearer tokenabc");
        request.addHeader("X-Api-Key", "apikey789");
        request.setContent("{\"password\":\"clave-del-body\"}".getBytes());

        filter.doFilter(request, response, (rq, rs) -> { });

        ILoggingEvent event = appender.list.get(0);
        String everything = event.getFormattedMessage() + event.getMDCPropertyMap();
        assertThat(everything).doesNotContain("secreto123", "clave456", "tokenabc", "apikey789", "clave-del-body");
    }

    @Test
    void lasRutasExcluidasNoSeRegistran() throws Exception {
        MockHttpServletRequest health = new MockHttpServletRequest("GET", "/actuator/health");

        filter.doFilter(health, response, (rq, rs) -> { });

        assertThat(appender.list).isEmpty();
    }

    @Test
    void elCorrelationIdDelMdcAcompaniaLaLinea() throws Exception {
        MDC.put("correlationId", "abc-123");

        filter.doFilter(request, response, (rq, rs) -> { });

        assertThat(appender.list.get(0).getMDCPropertyMap()).containsEntry("correlationId", "abc-123");
    }
}