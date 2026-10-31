package org.tudai.ticketya.web.user;

import jakarta.servlet.http.HttpServletRequest;
import org.tudai.ticketya.core.HeaderNames;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.InvalidUserContextException;

import java.util.Arrays;
import java.util.Locale;

public class HeaderUserContext implements UserContext {

    private final HttpServletRequest request;

    public HeaderUserContext(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public boolean isPresent() {
        return hasText(request.getHeader(HeaderNames.USER_ID))
                && hasText(request.getHeader(HeaderNames.USER_ROLE));
    }

    @Override
    public String userId() {
        return required(HeaderNames.USER_ID);
    }

    @Override
    public Role role() {
        String raw = required(HeaderNames.USER_ROLE);
        try {
            return Role.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidUserContextException("El header " + HeaderNames.USER_ROLE
                    + " no es válido. Valores permitidos: " + Arrays.toString(Role.values()));
        }
    }

    private String required(String header) {
        String value = request.getHeader(header);
        if (!hasText(value)) {
            throw new InvalidUserContextException("Falta el header obligatorio " + header);
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}