package org.tudai.ticketya.eventos.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.dto.lugar.LugarCreateDTO;
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
}
