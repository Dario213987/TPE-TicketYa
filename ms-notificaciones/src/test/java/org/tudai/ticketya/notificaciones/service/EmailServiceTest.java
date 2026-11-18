package org.tudai.ticketya.notificaciones.service;

import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.tudai.ticketya.notificaciones.exception.EmailConexionException;
import org.tudai.ticketya.notificaciones.exception.EmailPermanenteException;
import org.tudai.ticketya.notificaciones.exception.EmailTimeoutException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        lenient().when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        emailService = new EmailService(mailSender, "no-reply@ticketya.local", new EmailErrorClassifier());
    }

    @Test
    void enviaMimeMessage() {
        emailService.enviar("ana@test.com", "asunto", "<p>cuerpo</p>");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void falloDeConexionSeClasificaComoTransitorio() {
        doThrow(new MailSendException("down", new ConnectException("refused")))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.enviar("ana@test.com", "asunto", "<p>cuerpo</p>"))
                .isInstanceOf(EmailConexionException.class)
                .hasMessageContaining("refused");
    }

    @Test
    void timeoutSeClasificaComoAmbiguo() {
        doThrow(new MailSendException("slow", new SocketTimeoutException("timed out")))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.enviar("ana@test.com", "asunto", "<p>cuerpo</p>"))
                .isInstanceOf(EmailTimeoutException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void direccionInvalidaSeClasificaComoNoEntregable() {
        doThrow(new MailSendException("bad", new AddressException("invalid")))
                .when(mailSender).send(any(MimeMessage.class));

        EmailPermanenteException error = catchThrowableOfType(
                () -> emailService.enviar("ana@test.com", "asunto", "<p>cuerpo</p>"),
                EmailPermanenteException.class);

        assertThat(error.getTipo()).isEqualTo(EmailPermanenteException.Tipo.NO_ENTREGABLE);
    }

    @Test
    void autenticacionInvalidaSeClasificaComoFallido() {
        doThrow(new MailAuthenticationException("auth"))
                .when(mailSender).send(any(MimeMessage.class));

        EmailPermanenteException error = catchThrowableOfType(
                () -> emailService.enviar("ana@test.com", "asunto", "<p>cuerpo</p>"),
                EmailPermanenteException.class);

        assertThat(error.getTipo()).isEqualTo(EmailPermanenteException.Tipo.FALLIDO);
    }

    @Test
    void destinatarioVacioSeClasificaComoNoEntregable() {
        EmailPermanenteException error = catchThrowableOfType(
                () -> emailService.enviar("", "asunto", "<p>cuerpo</p>"),
                EmailPermanenteException.class);

        assertThat(error.getTipo()).isEqualTo(EmailPermanenteException.Tipo.NO_ENTREGABLE);
    }
}
