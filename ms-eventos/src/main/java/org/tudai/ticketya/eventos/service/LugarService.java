package org.tudai.ticketya.eventos.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.dto.lugar.LugarCreateDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarDetalleResponseDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarPageResponseDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.repository.LugarRepository;

@Service
@RequiredArgsConstructor
public class LugarService {

    private final LugarRepository lugarRepository;
    private final ModelMapper modelMapper;

    @Transactional
    public LugarResponseDTO crearLugar(LugarCreateDTO dto) {
        Lugar lugar = modelMapper.map(dto, Lugar.class);
        Lugar guardado = lugarRepository.save(lugar);
        return modelMapper.map(guardado, LugarResponseDTO.class);
    }

    @Transactional(readOnly = true)
    public LugarPageResponseDTO listarLugares(String ciudad, Pageable pageable) {
        Page<Lugar> page = (ciudad == null || ciudad.isBlank())
                ? lugarRepository.findAll(pageable)
                : lugarRepository.findByCiudadIgnoreCase(ciudad.trim(), pageable);

        return modelMapper.map(page, LugarPageResponseDTO.class);
    }

    @Transactional(readOnly = true)
    public LugarDetalleResponseDTO obtenerLugar(Long id) {
        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Lugar no encontrado con id: " + id));

        return modelMapper.map(lugar, LugarDetalleResponseDTO.class);
    }
}
