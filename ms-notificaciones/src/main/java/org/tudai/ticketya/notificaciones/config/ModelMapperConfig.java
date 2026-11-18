package org.tudai.ticketya.notificaciones.config;

import org.modelmapper.AbstractConverter;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Configuracion del {@link ModelMapper} usado para mapear entidades y DTOs.
 * <p>
 * Usa coincidencia estricta, ignora valores nulos y convierte {@link Instant} a
 * {@link OffsetDateTime} en UTC.
 */
@org.springframework.context.annotation.Configuration
public class ModelMapperConfig {

    /**
     * Crea el mapper con la configuracion comun del servicio.
     *
     * @return mapper configurado
     */
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setSkipNullEnabled(true)
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(Configuration.AccessLevel.PRIVATE);

        modelMapper.addConverter(new AbstractConverter<Instant, OffsetDateTime>() {
            /**
             * Convierte un instante a la misma marca de tiempo en UTC.
             *
             * @param source instante de origen
             * @return fecha con offset UTC o {@code null}
             */
            @Override
            protected OffsetDateTime convert(Instant source) {
                return source == null ? null : source.atOffset(ZoneOffset.UTC);
            }
        });
        return modelMapper;
    }
}
