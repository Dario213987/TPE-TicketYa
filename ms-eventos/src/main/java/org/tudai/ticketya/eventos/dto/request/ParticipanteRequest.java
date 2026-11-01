package org.tudai.ticketya.eventos.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tudai.ticketya.eventos.domain.TipoParticipante;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipanteRequest {
    @Positive(message = "El id del participante es inválido")
    private Long id;

    @Size(max = 150, message = "El nombre del participante no puede superar los 150 caracteres")
    private String nombre;

    private TipoParticipante tipo;

    @JsonIgnore
    @AssertTrue(message = "El participante debe venir con id, o bien con nombre y tipo")
    public boolean isValido() {
        boolean porId = id != null && nombre == null && tipo == null;
        boolean porNombre = id == null && nombre != null && !nombre.isBlank() && tipo != null;
        return porId || porNombre;
    }

}
