package org.tudai.ticketya.eventos.dto.participante;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipanteRequestDTO {

    private Long id;
    private String nombre;

    @JsonIgnore
    @AssertTrue(message = "Debe enviar el ID (participante existente) o el nombre (participante nuevo)")
    public boolean isValido() {

        if (this.id != null) {
            return true;
        }

        return this.nombre != null && !this.nombre.trim().isEmpty();
    }
}
