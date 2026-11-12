package org.tudai.ticketya.eventos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients(basePackages = "org.tudai.ticketya.eventos.client")
@EnableScheduling
public class EventosApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventosApplication.class, args);
    }
}
