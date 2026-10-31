package org.tudai.ticketya.web.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.core.exception.RemoteServiceException;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TicketyaErrorDecoderTest {

    private final TicketyaErrorDecoder decoder = new TicketyaErrorDecoder(new ObjectMapper());

    @Test
    void error404_seTraduceANotFoundConElDetalleRemoto() {
        Exception ex = decoder.decode("Api#get", response(404, "{\"detail\":\"Transacción 7 no existe\"}"));

        assertThat(ex).isInstanceOf(NotFoundException.class).hasMessage("Transacción 7 no existe");
    }

    @Test
    void error409_seTraduceAConflict() {
        assertThat(decoder.decode("Api#get", response(409, "{\"detail\":\"ya existe\"}")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void error400_seTraduceABadRequest() {
        assertThat(decoder.decode("Api#get", response(400, "{\"detail\":\"mal\"}")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void error403_seTraduceAForbidden() {
        assertThat(decoder.decode("Api#get", response(403, "{\"detail\":\"no\"}")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void error5xx_seTraduceARemoteServiceConElStatus() {
        Exception ex = decoder.decode("Api#get", response(503, "{\"detail\":\"interno\"}"));

        assertThat(ex).isInstanceOf(RemoteServiceException.class);
        assertThat(((RemoteServiceException) ex).getRemoteStatus()).isEqualTo(503);
    }

    @Test
    void cuerpoQueNoEsJson_usaMensajeGenerico() {
        Exception ex = decoder.decode("Api#get", response(404, "<html>no encontrado</html>"));

        assertThat(ex).isInstanceOf(NotFoundException.class).hasMessage("El servicio remoto respondió 404");
    }

    private static Response response(int status, String body) {
        Map<String, Collection<String>> noHeaders = Map.of();
        Request request = Request.create(Request.HttpMethod.GET, "/x", noHeaders, null,
                StandardCharsets.UTF_8, null);
        return Response.builder()
                .status(status)
                .request(request)
                .headers(noHeaders)
                .body(body, StandardCharsets.UTF_8)
                .build();
    }
}