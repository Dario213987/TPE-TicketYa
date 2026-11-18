package org.tudai.ticketya.eventos.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.eventos.service.EventoService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventoFinalizacionSchedulerTest {

    @Mock
    private EventoService eventoService;

    @InjectMocks
    private EventoFinalizacionScheduler scheduler;

    @Test
    void finalizarEventosVencidos_delegaEnElServicio() {
        scheduler.finalizarEventosVencidos();

        verify(eventoService).finalizarEventosVencidos();
    }
}
