package org.tudai.ticketya.tickets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableFeignClients(basePackages = "org.tudai.ticketya.contract")
public class TicketsApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketsApplication.class, args);
    }
}
