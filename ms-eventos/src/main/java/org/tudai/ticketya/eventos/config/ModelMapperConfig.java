package org.tudai.ticketya.eventos.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.tudai.ticketya.eventos.domain.Asiento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.SectorNumerado;
import org.tudai.ticketya.eventos.domain.TipoSector;
import org.tudai.ticketya.eventos.domain.Ubicacion;
import org.tudai.ticketya.eventos.dto.lugar.LugarCreateDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.dto.sector.SectorResponseDTO;

import java.util.stream.Collectors;

@org.springframework.context.annotation.Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STANDARD)
                .setSkipNullEnabled(true)
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(Configuration.AccessLevel.PRIVATE);

        modelMapper.createTypeMap(LugarCreateDTO.class, Lugar.class)
                .setPostConverter(context -> {
                    LugarCreateDTO source = context.getSource();
                    Lugar destination = context.getDestination();
                    destination.setUbicacion(Ubicacion.builder()
                            .latitud(source.getLatitud())
                            .longitud(source.getLongitud())
                            .build());
                    return destination;
                });

        modelMapper.createTypeMap(Lugar.class, LugarResponseDTO.class)
                .addMappings(mappings -> {
                    mappings.map(source -> source.getUbicacion().getLatitud(), LugarResponseDTO::setLatitud);
                    mappings.map(source -> source.getUbicacion().getLongitud(), LugarResponseDTO::setLongitud);
                });

        modelMapper.createTypeMap(SectorNoNumerado.class, SectorResponseDTO.class)
                .addMapping(source -> TipoSector.NO_NUMERADO, SectorResponseDTO::setTipoSector);

        modelMapper.createTypeMap(SectorNumerado.class, SectorResponseDTO.class)
                .addMappings(mappings -> {
                    mappings.map(source -> TipoSector.NUMERADO, SectorResponseDTO::setTipoSector);
                    mappings.map(source -> source.getAsientos().size(), SectorResponseDTO::setCapacidad);
                    mappings.map(ModelMapperConfig::cantidadDeFilas, SectorResponseDTO::setFilas);
                    mappings.map(ModelMapperConfig::asientosPorFila, SectorResponseDTO::setAsientosPorFila);
                });

        return modelMapper;
    }

    private static Integer cantidadDeFilas(SectorNumerado sector) {
        return sector.getAsientos().stream()
                .map(Asiento::getFila)
                .max(Integer::compareTo)
                .orElse(0);
    }

    private static Integer asientosPorFila(SectorNumerado sector) {
        return sector.getAsientos().stream()
                .collect(Collectors.groupingBy(Asiento::getFila, Collectors.counting()))
                .values().stream()
                .max(Long::compareTo)
                .orElse(0L)
                .intValue();
    }
}
