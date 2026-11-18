package org.tudai.ticketya.usuarios.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tudai.ticketya.usuarios.service.UsuarioService;

@Configuration
public class AdminInicialConfig {

    @Bean
    ApplicationRunner administradorInicial(UsuarioService service,
                                           @Value("${ticketya.usuarios.admin.email:}") String email,
                                           @Value("${ticketya.usuarios.admin.password:}") String password) {
        return args -> {
            if (!email.isBlank() && !password.isBlank()) {
                service.crearAdministradorSiNoExiste(email, password);
            }
        };
    }
}