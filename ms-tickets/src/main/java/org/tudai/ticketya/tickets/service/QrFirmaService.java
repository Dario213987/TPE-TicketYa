package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.tickets.config.QrProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class QrFirmaService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final QrProperties properties;

    public String firmar(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new BadRequestException("El contenido del QR es obligatorio");
        }
        return payload + "." + ENCODER.encodeToString(hmac(payload));
    }

    public String verificar(String codigoQr) {
        if (codigoQr == null || codigoQr.isBlank()) {
            throw new BadRequestException("El código QR es obligatorio");
        }

        int separador = codigoQr.lastIndexOf('.');
        if (separador <= 0 || separador == codigoQr.length() - 1) {
            throw firmaInvalida();
        }

        String payload = codigoQr.substring(0, separador);
        byte[] firmaRecibida;
        try {
            firmaRecibida = DECODER.decode(codigoQr.substring(separador + 1));
        } catch (IllegalArgumentException e) {
            throw firmaInvalida();
        }

        if (!MessageDigest.isEqual(hmac(payload), firmaRecibida)) {
            throw firmaInvalida();
        }
        return payload;
    }

    private byte[] hmac(String payload) {
        String secreto = properties.getSecreto();
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar tickets.qr.secreto (variable TICKETS_QR_SECRET)");
        }

        try {
            Mac mac = Mac.getInstance(properties.getAlgoritmo());
            mac.init(new SecretKeySpec(
                    secreto.getBytes(StandardCharsets.UTF_8), properties.getAlgoritmo()));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular la firma del QR", e);
        }
    }

    private BadRequestException firmaInvalida() {
        return new BadRequestException("La firma del QR es inválida");
    }
}
