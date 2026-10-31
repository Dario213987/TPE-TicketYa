package org.tudai.ticketya.web.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.core.exception.RemoteServiceException;

import java.io.IOException;
import java.io.InputStream;

public class TicketyaErrorDecoder implements ErrorDecoder {

    private final ObjectMapper mapper;

    public TicketyaErrorDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String detail = extractDetail(response);

        if (status >= 500) {
            return new RemoteServiceException(status, methodKey + " respondió " + status);
        }
        return switch (status) {
            case 400, 422 -> new BadRequestException(detail);
            case 401, 403 -> new ForbiddenException(detail);
            case 404 -> new NotFoundException(detail);
            case 409 -> new ConflictException(detail);
            default -> new RemoteServiceException(status, detail);
        };
    }

    private String extractDetail(Response response) {
        String fallback = "El servicio remoto respondió " + response.status();
        if (response.body() == null) {
            return fallback;
        }
        try (InputStream in = response.body().asInputStream()) {
            JsonNode root = mapper.readTree(in);
            if (root != null && root.path("detail").isTextual()) {
                return root.path("detail").asText();
            }
        } catch (IOException | RuntimeException ignored) {}
        return fallback;
    }
}