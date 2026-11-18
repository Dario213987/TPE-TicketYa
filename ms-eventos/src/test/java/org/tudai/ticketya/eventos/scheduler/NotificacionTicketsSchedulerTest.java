package org.tudai.ticketya.eventos.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.eventos.service.NotificacionTicketsService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificacionTicketsSchedulerTest {

    @Mock
    private NotificacionTicketsService notificacionTicketsService;

    @InjectMocks
    private NotificacionTicketsScheduler scheduler;

    @Test
    void procesarPendientes_delegaEnElServicio() {
        scheduler.procesarPendientes();

        verify(notificacionTicketsService).procesarPendientes();
    }
}
