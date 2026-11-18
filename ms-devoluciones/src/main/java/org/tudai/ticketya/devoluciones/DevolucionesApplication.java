package org.tudai.ticketya.devoluciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.devoluciones.client.EventoClient;
import org.tudai.ticketya.devoluciones.client.TicketClient;

@SpringBootApplication
@EnableFeignClients(clients = {TransaccionesApiClient.class, TicketClient.class, EventoClient.class})
@EnableScheduling
public class DevolucionesApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevolucionesApplication.class, args);
    }
}
