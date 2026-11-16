package org.tudai.ticketya.tickets.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "tickets.habilitacion")
public class HabilitacionProperties {

    private boolean enabled = true;

    private int tamanoTanda = 50;

    private Duration intervalo = Duration.ofSeconds(30);

    private Duration ttlPase = Duration.ofMinutes(5);
}
