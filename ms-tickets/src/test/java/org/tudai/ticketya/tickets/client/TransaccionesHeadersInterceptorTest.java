package org.tudai.ticketya.tickets.client;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransaccionesHeadersInterceptorTest {

    private final TransaccionesHeadersInterceptor interceptor = new TransaccionesHeadersInterceptor();

    @Test
    void comoOrganizador_reemplazaLosHeadersDelClientePorLosDelOrganizador() {
        RequestTemplate template = templateConHeadersDelCliente();

        TransaccionesHeadersInterceptor.comoOrganizador(5L, () -> {
            interceptor.apply(template);
            return null;
        });

        assertThat(template.headers().get("X-User-Id")).containsExactly("5");
        assertThat(template.headers().get("X-User-Role")).containsExactly("ORGANIZADOR");
    }

    @Test
    void fueraDeComoOrganizador_noModificaLosHeaders() {
        RequestTemplate template = templateConHeadersDelCliente();

        interceptor.apply(template);

        assertThat(template.headers().get("X-User-Id")).containsExactly("42");
        assertThat(template.headers().get("X-User-Role")).containsExactly("CLIENTE");
    }

    @Test
    void comoOrganizador_limpiaElOrganizadorAlTerminar() {
        TransaccionesHeadersInterceptor.comoOrganizador(5L, () -> null);
        RequestTemplate template = templateConHeadersDelCliente();

        interceptor.apply(template);

        assertThat(template.headers().get("X-User-Id")).containsExactly("42");
    }

    private RequestTemplate templateConHeadersDelCliente() {
        RequestTemplate template = new RequestTemplate();
        template.header("X-User-Id", "42");
        template.header("X-User-Role", "CLIENTE");
        return template;
    }
}
