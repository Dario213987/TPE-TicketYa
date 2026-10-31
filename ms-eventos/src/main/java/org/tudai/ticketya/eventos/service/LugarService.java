package org.tudai.ticketya.eventos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Ubicacion;
import org.tudai.ticketya.eventos.dto.lugar.LugarCreateDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.repository.LugarRepository;

@Service
@RequiredArgsConstructor
public class LugarService {

    private final LugarRepository lugarRepository;

    @Transactional
    public LugarResponseDTO crearLugar(LugarCreateDTO dto) {
        Lugar lugar = Lugar.builder()
                .nombre(dto.getNombre())
                .tipo(dto.getTipo())
                .ciudad(dto.getCiudad())
                .direccion(dto.getDireccion())
                .capacidad(dto.getCapacidad())
                .ubicacion(Ubicacion.builder()
                        .latitud(dto.getLatitud())
                        .longitud(dto.getLongitud())
                        .build())
                .build();

        Lugar guardado = lugarRepository.save(lugar);

        return LugarResponseDTO.builder()
                .id(guardado.getId())
                .nombre(guardado.getNombre())
                .tipo(guardado.getTipo())
                .ciudad(guardado.getCiudad())
                .direccion(guardado.getDireccion())
                .latitud(guardado.getUbicacion().getLatitud())
                .longitud(guardado.getUbicacion().getLongitud())
                .capacidad(guardado.getCapacidad())
                .build();
    }
}
