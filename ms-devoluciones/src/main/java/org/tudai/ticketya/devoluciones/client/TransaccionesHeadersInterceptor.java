package org.tudai.ticketya.devoluciones.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.tudai.ticketya.core.HeaderNames;
import org.tudai.ticketya.core.Role;

import java.util.function.Supplier;

/**
 * Interceptor exclusivo del cliente Feign de ms-transacciones.
 * <p>
 * ms-transacciones solo acepta el rol ORGANIZADOR y toma el X-User-Id como organizador.
 * El reintegro corre sin usuario autenticado, así que los headers se setean acá con el
 * organizador del evento. Se registra por configuración, solo para ese cliente.
 */
public class TransaccionesHeadersInterceptor implements RequestInterceptor {

    private static final ThreadLocal<Long> ORGANIZADOR = new ThreadLocal<>();

    public static <T> T comoOrganizador(Long organizadorId, Supplier<T> llamada) {
        ORGANIZADOR.set(organizadorId);
        try {
            return llamada.get();
        } finally {
            ORGANIZADOR.remove();
        }
    }

    @Override
    public void apply(RequestTemplate template) {
        Long organizadorId = ORGANIZADOR.get();
        if (organizadorId == null) {
            return;
        }
        template.removeHeader(HeaderNames.USER_ID);
        template.removeHeader(HeaderNames.USER_ROLE);
        template.header(HeaderNames.USER_ID, String.valueOf(organizadorId));
        template.header(HeaderNames.USER_ROLE, Role.ORGANIZADOR.name());
    }
}
