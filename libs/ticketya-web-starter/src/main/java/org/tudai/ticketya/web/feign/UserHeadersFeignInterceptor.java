package org.tudai.ticketya.web.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tudai.ticketya.core.HeaderNames;

import java.util.List;

public class UserHeadersFeignInterceptor implements RequestInterceptor {

    private static final List<String> PROPAGATED = List.of(HeaderNames.USER_ID, HeaderNames.USER_ROLE);

    @Override
    public void apply(RequestTemplate template) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            for (String name : PROPAGATED) {
                String value = request.getHeader(name);
                if (value != null && !value.isBlank()) {
                    template.header(name, value);
                }
            }
        }
    }
}