package org.tudai.ticketya.eventos.notificacion;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.eventos.service.NotificacionEventoPublisher;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion.COLA_ENVIAR;

class AvisoEventoListenerTest {

    @Test
    void listenerAnotadoAfterCommit() throws Exception {
        Method metodo = AvisoEventoListener.class.getMethod("onAviso", AvisoEventoEvent.class);
        TransactionalEventListener anotacion = metodo.getAnnotation(TransactionalEventListener.class);

        assertNotNull(anotacion);
        assertEquals(TransactionPhase.AFTER_COMMIT, anotacion.phase());
    }

    @Test
    void falloDeRabbitNoSePropaga() {
        NotificacionEventoPublisher publicador = mock(NotificacionEventoPublisher.class);
        AvisoEventoListener listener = new AvisoEventoListener(publicador, Runnable::run);
        doThrow(new RuntimeException("Rabbit caido")).when(publicador).publicar(any(), any(), any());

        assertDoesNotThrow(() -> listener.onAviso(
                new AvisoEventoEvent(MotivoNotificacion.PUBLICACION_ARTISTA, 1L,
                        "MSE-PUBLICACION_ARTISTA-1")));
    }

    @Test
    void rechazoDelExecutorNoSePropaga() {
        NotificacionEventoPublisher publicador = mock(NotificacionEventoPublisher.class);
        Executor rechazante = tarea -> {
            throw new RejectedExecutionException("cola llena");
        };
        AvisoEventoListener listener = new AvisoEventoListener(publicador, rechazante);

        assertDoesNotThrow(() -> listener.onAviso(new AvisoEventoEvent(
                MotivoNotificacion.PUBLICACION_ARTISTA, 1L, "MSE-PUBLICACION_ARTISTA-1")));
        verifyNoInteractions(publicador);
    }

    @Test
    void commitPublicaYRollbackNoPublica() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AnnotationConfigApplicationContext contexto = new AnnotationConfigApplicationContext();
        contexto.register(TestConfig.class);
        contexto.getBeanFactory().registerSingleton("rabbitTemplate", rabbitTemplate);
        contexto.refresh();

        AvisoEventoEvent aviso = new AvisoEventoEvent(
                MotivoNotificacion.PUBLICACION_ARTISTA, 10L, "MSE-PUBLICACION_ARTISTA-10");

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            contexto.publishEvent(aviso);
            List<TransactionSynchronization> sincronizaciones =
                    List.copyOf(TransactionSynchronizationManager.getSynchronizations());
            sincronizaciones.forEach(TransactionSynchronization::afterCommit);
            sincronizaciones.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verify(rabbitTemplate).convertAndSend(eq(COLA_ENVIAR), any(NotificacionEntranteDTO.class));

        reset(rabbitTemplate);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            contexto.publishEvent(aviso);
            List<TransactionSynchronization> sincronizaciones =
                    List.copyOf(TransactionSynchronizationManager.getSynchronizations());
            sincronizaciones.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verify(rabbitTemplate, never()).convertAndSend(eq(COLA_ENVIAR), any(NotificacionEntranteDTO.class));

        contexto.close();
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        static TransactionalEventListenerFactory transactionalEventListenerFactory() {
            return new TransactionalEventListenerFactory();
        }

        @Bean
        NotificacionEventoPublisher publicador(RabbitTemplate rabbitTemplate) {
            return new NotificacionEventoPublisher(rabbitTemplate);
        }

        @Bean
        Executor notificacionesAsyncExecutor() {
            return Runnable::run;
        }

        @Bean
        AvisoEventoListener listener(NotificacionEventoPublisher publicador, Executor notificacionesAsyncExecutor) {
            return new AvisoEventoListener(publicador, notificacionesAsyncExecutor);
        }
    }
}
