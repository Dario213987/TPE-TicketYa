package org.tudai.ticketya.eventos.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.SectorNumerado;
import org.tudai.ticketya.eventos.dto.sector.SectorNoNumeradoRequestDTO;
import org.tudai.ticketya.eventos.dto.sector.SectorNumeradoRequestDTO;
import org.tudai.ticketya.eventos.dto.sector.SectorResponseDTO;
import org.tudai.ticketya.eventos.repository.LugarRepository;

@Service
@RequiredArgsConstructor
public class SectorService {

    private final LugarRepository lugarRepository;
    private final ModelMapper modelMapper;

    @Transactional
    public SectorResponseDTO crearSectorNoNumerado(Long lugarId, SectorNoNumeradoRequestDTO dto) {
        Lugar lugar = lugarRepository.findById(lugarId)
                .orElseThrow(() -> new EntityNotFoundException("Lugar no encontrado con id: " + lugarId));

        SectorNoNumerado sector = modelMapper.map(dto, SectorNoNumerado.class);

        lugar.addSector(sector);
        lugarRepository.save(lugar);

        return modelMapper.map(sector, SectorResponseDTO.class);
    }

    @Transactional
    public SectorResponseDTO crearSectorNumerado(Long lugarId, SectorNumeradoRequestDTO dto) {
        Lugar lugar = lugarRepository.findById(lugarId)
                .orElseThrow(() -> new EntityNotFoundException("Lugar no encontrado con id: " + lugarId));

        SectorNumerado sector = modelMapper.map(dto, SectorNumerado.class);
        sector.generarAsientos(dto.getFilas(), dto.getAsientosPorFila());

        lugar.addSector(sector);
        lugarRepository.save(lugar);

        return modelMapper.map(sector, SectorResponseDTO.class);
    }
}
