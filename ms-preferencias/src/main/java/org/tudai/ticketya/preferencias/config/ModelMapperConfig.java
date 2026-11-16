package org.tudai.ticketya.preferencias.config;

import org.modelmapper.AbstractConverter;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * ModelMapper del servicio, con la misma configuración que el resto de los
 * microservicios: matching estricto por nombre, sin pisar con nulos y con
 * acceso a los campos privados.
 */
@org.springframework.context.annotation.Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setSkipNullEnabled(true)
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(Configuration.AccessLevel.PRIVATE);

        // El contrato genera los date-time como LocalDateTime; el dominio y Mongo usan Instant.
        modelMapper.addConverter(new AbstractConverter<Instant, LocalDateTime>() {
            @Override
            protected LocalDateTime convert(Instant source) {
                return source == null ? null : LocalDateTime.ofInstant(source, ZoneOffset.UTC);
            }
        });

        return modelMapper;
    }
}
