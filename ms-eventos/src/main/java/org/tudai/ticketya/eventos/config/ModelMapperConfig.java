package org.tudai.ticketya.eventos.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.tudai.ticketya.eventos.domain.Asiento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Sector;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.SectorNumerado;
import org.tudai.ticketya.eventos.domain.TipoSector;
import org.tudai.ticketya.eventos.domain.Ubicacion;
import org.tudai.ticketya.eventos.dto.lugar.LugarCreateDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarDetalleResponseDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.dto.sector.SectorResponseDTO;

import java.util.Comparator;
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
                .setConverter(context -> {
                    SectorNoNumerado source = context.getSource();
                    SectorResponseDTO destination = new SectorResponseDTO();
                    destination.setId(source.getId());
                    destination.setNombre(source.getNombre());
                    destination.setPrecio(source.getPrecio());
                    destination.setTipoSector(TipoSector.NO_NUMERADO);
                    destination.setCapacidadMaxima(source.getCapacidadMaxima());
                    destination.setCapacidad(source.getCapacidadMaxima());
                    return destination;
                });

        modelMapper.createTypeMap(SectorNumerado.class, SectorResponseDTO.class)
                .setConverter(context -> {
                    SectorNumerado source = context.getSource();
                    SectorResponseDTO destination = new SectorResponseDTO();
                    destination.setId(source.getId());
                    destination.setNombre(source.getNombre());
                    destination.setPrecio(source.getPrecio());
                    destination.setTipoSector(TipoSector.NUMERADO);
                    destination.setCapacidad(source.getAsientos() == null ? 0 : source.getAsientos().size());
                    destination.setFilas(cantidadDeFilas(source));
                    destination.setAsientosPorFila(asientosPorFila(source));
                    return destination;
                });

        modelMapper.createTypeMap(Lugar.class, LugarDetalleResponseDTO.class)
                .addMappings(mappings -> {
                    mappings.map(source -> source.getUbicacion().getLatitud(), LugarDetalleResponseDTO::setLatitud);
                    mappings.map(source -> source.getUbicacion().getLongitud(), LugarDetalleResponseDTO::setLongitud);
                    mappings.skip(LugarDetalleResponseDTO::setSectores);
                })
                .setPostConverter(context -> {
                    Lugar source = context.getSource();
                    LugarDetalleResponseDTO destination = context.getDestination();
                    destination.setSectores(source.getSectores().stream()
                            .sorted(Comparator.comparing(Sector::getNombre,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .map(sector -> modelMapper.map(sector, SectorResponseDTO.class))
                            .toList());
                    return destination;
                });

        return modelMapper;
    }

    private static Integer cantidadDeFilas(SectorNumerado sector) {
        if (sector.getAsientos() == null) {
            return 0;
        }
        return sector.getAsientos().stream()
                .map(Asiento::getFila)
                .max(Integer::compareTo)
                .orElse(0);
    }

    private static Integer asientosPorFila(SectorNumerado sector) {
        if (sector.getAsientos() == null) {
            return 0;
        }
        return sector.getAsientos().stream()
                .collect(Collectors.groupingBy(Asiento::getFila, Collectors.counting()))
                .values().stream()
                .max(Long::compareTo)
                .orElse(0L)
                .intValue();
    }
}
