package org.tudai.ticketya.preferencias;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.tudai.ticketya.contract.eventos.api.EventosApiClient;

@SpringBootApplication
@EnableFeignClients(clients = EventosApiClient.class)
public class MsPreferenciasApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsPreferenciasApplication.class, args);
    }

}
