package org.tudai.ticketya.devoluciones.config;

import org.modelmapper.AbstractConverter;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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

        modelMapper.addConverter(new AbstractConverter<Instant, OffsetDateTime>() {
            @Override
            protected OffsetDateTime convert(Instant source) {
                return source == null ? null : source.atOffset(ZoneOffset.UTC);
            }
        });

        modelMapper.addConverter(new AbstractConverter<LocalDateTime, OffsetDateTime>() {
            @Override
            protected OffsetDateTime convert(LocalDateTime source) {
                return source == null ? null : source.atOffset(ZoneOffset.UTC);
            }
        });

        return modelMapper;
    }
}
