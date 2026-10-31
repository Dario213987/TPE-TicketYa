package org.tudai.ticketya.core;


import org.tudai.ticketya.core.exception.InvalidUserContextException;

public interface UserContext {

    boolean isPresent();

    /** @throws InvalidUserContextException si falta el usuario */
    String userId();

    /** @throws InvalidUserContextException si falta el rol o no es válido */
    Role role();
}