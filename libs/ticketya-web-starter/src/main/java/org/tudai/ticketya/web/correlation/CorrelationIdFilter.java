package org.tudai.ticketya.web.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tudai.ticketya.core.CorrelationId;
import org.tudai.ticketya.core.HeaderNames;
import org.tudai.ticketya.core.MdcKeys;

import java.io.IOException;

public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = CorrelationId.resolve(request.getHeader(HeaderNames.CORRELATION_ID));
        MDC.put(MdcKeys.CORRELATION_ID, id);
        response.setHeader(HeaderNames.CORRELATION_ID, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}