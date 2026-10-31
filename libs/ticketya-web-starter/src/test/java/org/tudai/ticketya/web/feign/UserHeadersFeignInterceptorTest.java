package org.tudai.ticketya.web.feign;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class UserHeadersFeignInterceptorTest {

    private final UserHeadersFeignInterceptor interceptor = new UserHeadersFeignInterceptor();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void copiaUsuarioYRolDeLaRequestEntrante() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-User-Role", "ORGANIZADOR");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-User-Id")).containsExactly("42");
        assertThat(template.headers().get("X-User-Role")).containsExactly("ORGANIZADOR");
    }

    @Test
    void sinRequestEnCurso_noAgregaNadaNiFalla() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKeys("X-User-Id", "X-User-Role");
    }
}