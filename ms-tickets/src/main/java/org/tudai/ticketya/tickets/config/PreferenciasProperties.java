package org.tudai.ticketya.tickets.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "tickets.preferencias")
public class PreferenciasProperties {

    private boolean enabled = true;

    private String streamTicketUtilizado = "ticket.utilizado";

    private String redisHost = "localhost";

    private int redisPort = 6381;
}
