package org.tudai.ticketya.web.security;

import org.tudai.ticketya.core.Role;

public class AuthorizationRoot {

    private final Role role;

    public AuthorizationRoot(Role role) {
        this.role = role;
    }

    public boolean hasRole(String name) {
        return role == parse(name);
    }

    public boolean hasAnyRole(String... names) {
        for (String name : names) {
            if (hasRole(name)) {
                return true;
            }
        }
        return false;
    }

    private static Role parse(String name) {
        try {
            return Role.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rol desconocido en @Authorize: " + name);
        }
    }
}