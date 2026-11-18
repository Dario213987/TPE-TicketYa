package org.tudai.ticketya.tickets.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "tickets.qr")
public class QrProperties {

    private String secreto;

    private String algoritmo = "HmacSHA256";
}
