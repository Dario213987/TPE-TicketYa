package org.tudai.ticketya.eventos.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.eventos.model.SectorNoNumeradoRequest;
import org.tudai.ticketya.contract.eventos.model.SectorNumeradoRequest;
import org.tudai.ticketya.contract.eventos.model.SectorResponse;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.SectorNumerado;
import org.tudai.ticketya.eventos.repository.LugarRepository;

@Service
@RequiredArgsConstructor
public class SectorService {

    private final LugarRepository lugarRepository;
    private final ModelMapper modelMapper;

    @Transactional
    public SectorResponse crearSectorNoNumerado(Long lugarId, SectorNoNumeradoRequest dto) {
        Lugar lugar = lugarRepository.findById(lugarId)
                .orElseThrow(() -> new NotFoundException("Lugar no encontrado con id: " + lugarId));

        SectorNoNumerado sector = modelMapper.map(dto, SectorNoNumerado.class);

        lugar.addSector(sector);
        lugarRepository.save(lugar);

        return modelMapper.map(sector, SectorResponse.class);
    }

    @Transactional
    public SectorResponse crearSectorNumerado(Long lugarId, SectorNumeradoRequest dto) {
        Lugar lugar = lugarRepository.findById(lugarId)
                .orElseThrow(() -> new NotFoundException("Lugar no encontrado con id: " + lugarId));

        SectorNumerado sector = modelMapper.map(dto, SectorNumerado.class);
        sector.generarAsientos(dto.getFilas(), dto.getAsientosPorFila());

        lugar.addSector(sector);
        lugarRepository.save(lugar);

        return modelMapper.map(sector, SectorResponse.class);
    }
}
