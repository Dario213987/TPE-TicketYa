package org.tudai.ticketya.eventos.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.tudai.ticketya.contract.eventos.model.EventoResponse;
import org.tudai.ticketya.contract.eventos.model.LugarCreateRequest;
import org.tudai.ticketya.contract.eventos.model.LugarDetalleResponse;
import org.tudai.ticketya.contract.eventos.model.LugarResponse;
import org.tudai.ticketya.contract.eventos.model.ParticipanteResponse;
import org.tudai.ticketya.contract.eventos.model.SectorResponse;
import org.tudai.ticketya.contract.eventos.model.TipoSector;
import org.tudai.ticketya.eventos.domain.Asiento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.domain.Sector;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.SectorNumerado;
import org.tudai.ticketya.eventos.domain.Ubicacion;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
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

        modelMapper.createTypeMap(LugarCreateRequest.class, Lugar.class)
                .setPostConverter(context -> {
                    LugarCreateRequest source = context.getSource();
                    Lugar destination = context.getDestination();
                    destination.setUbicacion(Ubicacion.builder()
                            .latitud(source.getLatitud())
                            .longitud(source.getLongitud())
                            .build());
                    return destination;
                });

        modelMapper.createTypeMap(Evento.class, EventoResponse.class)
                .setConverter(context -> {
                    Evento source = context.getSource();
                    EventoResponse destination = new EventoResponse();
                    destination.setId(source.getId());
                    destination.setNombre(source.getNombre());
                    destination.setDescripcion(source.getDescripcion());
                    destination.setTipo(source.getTipo());
                    destination.setFecha(source.getFecha());
                    destination.setEstado(source.getEstado());
                    destination.setOrganizadorId(source.getOrganizadorId());
                    destination.setAltaDemanda(source.isAltaDemanda());
                    destination.setLugar(source.getLugar() == null
                            ? null
                            : modelMapper.map(source.getLugar(), LugarResponse.class));
                    destination.setParticipantes(source.getParticipantes().stream()
                            .sorted(Comparator.comparing(Participante::getId,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .map(participante -> new ParticipanteResponse(participante.getId(), participante.getNombre()))
                            .toList());
                    destination.setSectores(source.getPreciosSectores().entrySet().stream()
                            .map(entry -> {
                                Sector sector = sectoresDe(source.getLugar()).stream()
                                        .filter(candidato -> Objects.equals(candidato.getId(), entry.getKey()))
                                        .findFirst()
                                        .orElse(null);
                                if (sector == null) {
                                    return null;
                                }
                                SectorResponse response = modelMapper.map(sector, SectorResponse.class);
                                response.setPrecio(entry.getValue());
                                return response;
                            })
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparing(SectorResponse::getNombre,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList());
                    destination.setPreciosSectores(source.getPreciosSectores().entrySet().stream()
                            .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue)));
                    return destination;
                });

        modelMapper.createTypeMap(Lugar.class, LugarResponse.class)
                .setConverter(context -> {
                    Lugar source = context.getSource();
                    LugarResponse destination = new LugarResponse();
                    destination.setId(source.getId());
                    destination.setNombre(source.getNombre());
                    destination.setTipo(source.getTipo());
                    destination.setDireccion(source.getDireccion());
                    destination.setCiudad(source.getCiudad());
                    destination.setCapacidad(source.getCapacidad());
                    destination.setLatitud(latitud(source));
                    destination.setLongitud(longitud(source));
                    return destination;
                });

        modelMapper.createTypeMap(SectorNoNumerado.class, SectorResponse.class)
                .setConverter(context -> {
                    SectorNoNumerado source = context.getSource();
                    SectorResponse destination = new SectorResponse();
                    destination.setId(source.getId());
                    destination.setNombre(source.getNombre());
                    destination.setPrecio(source.getPrecio());
                    destination.setTipoSector(TipoSector.NO_NUMERADO);
                    destination.setCapacidadMaxima(source.getCapacidadMaxima());
                    destination.setCapacidad(source.getCapacidadMaxima());
                    return destination;
                });

        modelMapper.createTypeMap(SectorNumerado.class, SectorResponse.class)
                .setConverter(context -> {
                    SectorNumerado source = context.getSource();
                    SectorResponse destination = new SectorResponse();
                    destination.setId(source.getId());
                    destination.setNombre(source.getNombre());
                    destination.setPrecio(source.getPrecio());
                    destination.setTipoSector(TipoSector.NUMERADO);
                    destination.setCapacidad(source.getAsientos() == null ? 0 : source.getAsientos().size());
                    destination.setFilas(cantidadDeFilas(source));
                    destination.setAsientosPorFila(asientosPorFila(source));
                    return destination;
                });

        modelMapper.createTypeMap(Lugar.class, LugarDetalleResponse.class)
                .setConverter(context -> {
                    Lugar source = context.getSource();
                    LugarDetalleResponse destination = new LugarDetalleResponse();
                    destination.setId(source.getId());
                    destination.setNombre(source.getNombre());
                    destination.setTipo(source.getTipo());
                    destination.setDireccion(source.getDireccion());
                    destination.setCiudad(source.getCiudad());
                    destination.setCapacidad(source.getCapacidad());
                    destination.setLatitud(latitud(source));
                    destination.setLongitud(longitud(source));
                    destination.setSectores(sectoresDe(source).stream()
                            .sorted(Comparator.comparing(Sector::getNombre,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .map(sector -> modelMapper.map(sector, SectorResponse.class))
                            .toList());
                    return destination;
                });

        return modelMapper;
    }

    private static Double latitud(Lugar lugar) {
        return lugar.getUbicacion() == null ? null : lugar.getUbicacion().getLatitud();
    }

    private static Double longitud(Lugar lugar) {
        return lugar.getUbicacion() == null ? null : lugar.getUbicacion().getLongitud();
    }

    private static java.util.Set<Sector> sectoresDe(Lugar lugar) {
        return lugar.getSectores() == null ? java.util.Set.of() : lugar.getSectores();
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
