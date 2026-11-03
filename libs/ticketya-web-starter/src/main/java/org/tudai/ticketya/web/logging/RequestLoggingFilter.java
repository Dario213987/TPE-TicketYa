package org.tudai.ticketya.web.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tudai.ticketya.core.RequestLogger;
import org.tudai.ticketya.core.UserContext;

import java.io.IOException;

public class RequestLoggingFilter extends OncePerRequestFilter {

    private final UserContext userContext;
    private final String[] excludePaths;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RequestLoggingFilter(UserContext userContext, String[] excludePaths) {
        this.userContext = userContext;
        this.excludePaths = excludePaths;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : excludePaths) {
            if (matcher.match(pattern.trim(), path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            failed = true;   // la excepción llegó hasta acá: el contenedor responderá 500
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            int status = failed ? 500 : response.getStatus();
            RequestLogger.log(request.getMethod(), request.getRequestURI(), currentUser(), status, durationMs);
        }
    }

    private String currentUser() {
        try {
            return userContext.isPresent() ? userContext.userId() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}