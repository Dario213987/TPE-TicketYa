package org.tudai.ticketya.notificaciones.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ticketya.notificaciones.reintentos")
public class ReintentosNotificacionProperties {

    private int maxIntentos = 8;
    private long intervaloInicialMs = 5000L;
    private double multiplicador = 2.0;
    private long intervaloMaximoMs = 300000L;
}
