package org.tudai.ticketya.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Punto de entrada de ms-notificaciones.
 * <p>
 * Habilita los clientes Feign del paquete {@code client} para enriquecer los mensajes
 * con datos de eventos y usuarios antes de renderizar y enviar el email.
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "org.tudai.ticketya.notificaciones.client")
public class NotificacionesApplication {

    /**
     * Inicia la aplicacion Spring Boot.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificacionesApplication.class, args);
    }
}
