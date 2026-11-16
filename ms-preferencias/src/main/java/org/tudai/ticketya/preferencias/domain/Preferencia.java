package org.tudai.ticketya.preferencias.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "preferencias")
public class Preferencia {

    @Id
    private Long clienteId;

    @Builder.Default
    private List<TipoEvento> tiposFavoritos = new ArrayList<>();

    @Builder.Default
    private List<Long> artistasFavoritos = new ArrayList<>();

    private String ciudad;
}
