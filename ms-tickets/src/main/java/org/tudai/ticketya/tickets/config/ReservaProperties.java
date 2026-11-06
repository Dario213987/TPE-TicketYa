package org.tudai.ticketya.tickets.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "tickets.reserva")
public class ReservaProperties {

    private int limitePorCliente = 4;

    private Duration expiracion = Duration.ofMinutes(15);
}
