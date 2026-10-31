package org.tudai.ticketya.eventos.dto.lugar;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LugarPageResponseDTO {
    private List<LugarResponseDTO> content;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
}
