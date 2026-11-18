package org.tudai.ticketya.notificaciones.service;

import jakarta.mail.internet.AddressException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.notificaciones.exception.EmailConexionException;
import org.tudai.ticketya.notificaciones.exception.EmailPermanenteException;
import org.tudai.ticketya.notificaciones.exception.EmailPermanenteException.Tipo;
import org.tudai.ticketya.notificaciones.exception.EmailTimeoutException;
import org.tudai.ticketya.notificaciones.util.ResumidorErrores;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

@Component
public class EmailErrorClassifier {

    public RuntimeException clasificar(Exception error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (esTimeout(actual)) {
                return new EmailTimeoutException("Timeout de SMTP: " + ResumidorErrores.resumir(error), error);
            }
            Tipo tipo = tipoPermanente(actual);
            if (tipo != null) {
                return new EmailPermanenteException("Error permanente de SMTP: " + ResumidorErrores.resumir(error), error, tipo);
            }
            if (esConexion(actual)) {
                return new EmailConexionException("Error de conexion SMTP: " + ResumidorErrores.resumir(error), error);
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return new EmailConexionException("Error de conexion SMTP: " + ResumidorErrores.resumir(error), error);
    }

    private boolean esTimeout(Throwable error) {
        return error instanceof SocketTimeoutException
                || contiene(error, "timeout", "timed out");
    }

    private Tipo tipoPermanente(Throwable error) {
        if (error instanceof AddressException
                || error instanceof IllegalArgumentException) {
            return Tipo.NO_ENTREGABLE;
        }
        if (error instanceof MailAuthenticationException
                || error instanceof MailParseException
                || error instanceof MailPreparationException) {
            return Tipo.FALLIDO;
        }
        if (error instanceof SMTPSendFailedException
                && ((SMTPSendFailedException) error).getReturnCode() >= 500) {
            return Tipo.FALLIDO;
        }
        if (error instanceof SMTPAddressFailedException
                && ((SMTPAddressFailedException) error).getReturnCode() >= 500) {
            return Tipo.NO_ENTREGABLE;
        }
        return null;
    }

    private boolean esConexion(Throwable error) {
        if (error instanceof ConnectException
                || error instanceof NoRouteToHostException
                || error instanceof UnknownHostException
                || error instanceof SocketException) {
            return true;
        }
        if (error instanceof SMTPSendFailedException) {
            return ((SMTPSendFailedException) error).getReturnCode() >= 400;
        }
        return error instanceof SMTPAddressFailedException;
    }

    private boolean contiene(Throwable error, String... textos) {
        String mensaje = error.getMessage();
        if (mensaje == null) {
            return false;
        }
        String normalizado = mensaje.toLowerCase(Locale.ROOT);
        for (String texto : textos) {
            if (normalizado.contains(texto)) {
                return true;
            }
        }
        return false;
    }
}
