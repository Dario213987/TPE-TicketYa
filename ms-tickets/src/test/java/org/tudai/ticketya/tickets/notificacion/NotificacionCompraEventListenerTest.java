package org.tudai.ticketya.tickets.notificacion;

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
import org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificacionCompraEventListenerTest {

    @Test
    void listenerAnotadoAfterCommit() throws Exception {
        Method m = NotificacionCompraEventListener.class.getMethod("onCompraConfirmada", CompraConfirmadaNotificacionEvent.class);
        TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);
        assertNotNull(ann);
        assertEquals(TransactionPhase.AFTER_COMMIT, ann.phase());
    }

    @Test
    void commitPublicaYRollbackNoPublica() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(TestConfig.class);
        ctx.getBeanFactory().registerSingleton("rabbitTemplate", rabbitTemplate);
        ctx.refresh();

        CompraConfirmadaNotificacionEvent event = new CompraConfirmadaNotificacionEvent(99L, 10L, 20L, 5L, List.of(), 1, new BigDecimal("100"));

        // commit: se ejecuta afterCommit de las sincronizaciones registradas por @TransactionalEventListener
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            ctx.publishEvent(event);
            List<TransactionSynchronization> syncs = List.copyOf(TransactionSynchronizationManager.getSynchronizations());
            syncs.forEach(TransactionSynchronization::afterCommit);
            syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verify(rabbitTemplate, times(1)).convertAndSend(eq(ColasNotificacion.COLA_ENVIAR), any(NotificacionEntranteDTO.class));

        // rollback: no debe publicarse
        reset(rabbitTemplate);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            ctx.publishEvent(event);
            List<TransactionSynchronization> syncs = List.copyOf(TransactionSynchronizationManager.getSynchronizations());
            syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(Object.class));

        ctx.close();
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        static TransactionalEventListenerFactory transactionalEventListenerFactory() {
            return new TransactionalEventListenerFactory();
        }

        @Bean
        NotificacionCompraMensajeFactory factory() {
            return new NotificacionCompraMensajeFactory();
        }

        @Bean
        NotificacionCompraPublicador publicador(NotificacionCompraMensajeFactory factory, RabbitTemplate rabbitTemplate) {
            return new NotificacionCompraPublicador(factory, rabbitTemplate);
        }

        @Bean
        NotificacionCompraEventListener listener(NotificacionCompraPublicador publicador) {
            return new NotificacionCompraEventListener(publicador);
        }
    }
}
