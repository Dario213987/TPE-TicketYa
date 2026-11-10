package org.tudai.ticketya.core;


import org.tudai.ticketya.core.exception.InvalidUserContextException;

import java.util.Optional;

public interface UserContext {

    boolean isPresent();

    /** @throws InvalidUserContextException si falta el usuario */
    Long userId();

    /** @throws InvalidUserContextException si falta el rol o no es válido */
    Role role();

    default Optional<Long> findUserId() {
        try {
            return Optional.of(userId());
        } catch (InvalidUserContextException e) {
            return Optional.empty();
        }
    }
}