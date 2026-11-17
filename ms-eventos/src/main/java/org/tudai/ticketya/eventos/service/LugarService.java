package org.tudai.ticketya.eventos.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.eventos.model.LugarCreateRequest;
import org.tudai.ticketya.contract.eventos.model.LugarDetalleResponse;
import org.tudai.ticketya.contract.eventos.model.LugarResponse;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.exception.LugarNoEncontradoException;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LugarService {

    private final LugarRepository lugarRepository;
    private final ModelMapper modelMapper;

    @Transactional
    public LugarResponse crearLugar(LugarCreateRequest dto) {
        Lugar lugar = modelMapper.map(dto, Lugar.class);
        Lugar guardado = lugarRepository.save(lugar);
        return modelMapper.map(guardado, LugarResponse.class);
    }

    @Transactional(readOnly = true)
    public Page<LugarDetalleResponse> listarLugares(String ciudad, Pageable pageable) {
        Page<Lugar> page = (ciudad == null || ciudad.isBlank())
                ? lugarRepository.findAll(pageable)
                : lugarRepository.findByCiudadIgnoreCase(ciudad.trim(), pageable);
        return page.map(e -> modelMapper.map(e, LugarDetalleResponse.class));
    }

    @Transactional(readOnly = true)
    public LugarDetalleResponse obtenerLugar(Long id) {
        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new LugarNoEncontradoException("Lugar no encontrado con id: " + id));

        return modelMapper.map(lugar, LugarDetalleResponse.class);
    }

    @Transactional(readOnly = true)
    public Lugar obtenerPorId(Long id) {
        return lugarRepository.findById(id)
                .orElseThrow(() -> new LugarNoEncontradoException("No existe un lugar con el id " + id));
    }

}
