package org.tudai.ticketya.notificaciones.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envio de emails HTML por SMTP.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;
    private final EmailErrorClassifier emailErrorClassifier;

    /**
     * Crea el servicio con el remitente configurado.
     *
     * @param mailSender cliente SMTP
     * @param from       direccion remitente
     */
    public EmailService(JavaMailSender mailSender, @Value("${ticketya.mail.from}") String from,
            EmailErrorClassifier emailErrorClassifier) {
        this.mailSender = mailSender;
        this.from = from;
        this.emailErrorClassifier = emailErrorClassifier;
    }

    /**
     * Arma y envia un email HTML.
     *
     * @param destinatario direccion del destinatario
     * @param asunto       asunto del email
     * @param html         cuerpo HTML ya renderizado
     * @throws RuntimeException si falla el envio SMTP
     */
    public void enviar(String destinatario, String asunto, String html) {
        try {
            if (destinatario == null || destinatario.isBlank()) {
                throw new IllegalArgumentException("El destinatario del email es obligatorio");
            }
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw emailErrorClassifier.clasificar(e);
        }
    }
}
