package org.tudai.ticketya.notificaciones.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;

/**
 * Cliente Feign para obtener el destinatario desde {@code ms-usuarios}.
 */
@FeignClient(name = "ms-usuarios")
public interface UsuarioClient {

    /**
     * Obtiene el nombre y el email del usuario que recibira la notificacion.
     *
     * @param id id del usuario destinatario
     * @return datos del usuario
     */
    @GetMapping("/api/internal/usuarios/{id}")
    UsuarioClientDTO obtenerUsuario(@PathVariable("id") Long id);
}
