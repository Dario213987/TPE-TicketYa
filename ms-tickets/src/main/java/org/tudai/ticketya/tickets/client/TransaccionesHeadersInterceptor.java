package org.tudai.ticketya.tickets.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.tudai.ticketya.core.HeaderNames;
import org.tudai.ticketya.core.Role;

import java.util.function.Supplier;

/**
 * Interceptor exclusivo del cliente Feign de ms-transacciones.
 * <p>
 * ms-transacciones solo acepta el rol ORGANIZADOR y toma el X-User-Id como organizador
 * de la transacción. El interceptor global de libs reenvía los headers del usuario que hizo
 * el request (el cliente que paga), así que este los reemplaza por los del organizador del
 * evento. Se registra por configuración
 * ({@code spring.cloud.openfeign.client.config.transacciones.request-interceptors})
 * para que se aplique después del global y solo a ese cliente.
 */
public class TransaccionesHeadersInterceptor implements RequestInterceptor {

    private static final ThreadLocal<Long> ORGANIZADOR = new ThreadLocal<>();

    /**
     * Ejecuta la llamada a ms-transacciones en nombre del organizador indicado.
     *
     * @param organizadorId organizador del evento
     * @param llamada       llamada al cliente Feign
     *
     * @return el resultado de la llamada
     */
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
