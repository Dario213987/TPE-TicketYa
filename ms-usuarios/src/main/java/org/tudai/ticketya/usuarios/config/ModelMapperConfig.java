package org.tudai.ticketya.usuarios.config;

import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tudai.ticketya.contract.usuarios.model.UsuarioResponse;
import org.tudai.ticketya.usuarios.domain.Usuario;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Configuration
public class ModelMapperConfig {

    @Bean
    ModelMapper modelMapper() {
        Converter<Instant, OffsetDateTime> aOffset =
                ctx -> ctx.getSource() == null ? null : OffsetDateTime.ofInstant(ctx.getSource(), ZoneOffset.UTC);

        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        mapper.typeMap(Usuario.class, UsuarioResponse.class).addMappings(m -> {
            m.using(aOffset).map(Usuario::getCreadoEl, UsuarioResponse::setCreadaEn);
            m.using(aOffset).map(Usuario::getActualizadoEl, UsuarioResponse::setActualizadaEn);
        });
        return mapper;
    }
}
