package org.tudai.ticketya.notificaciones.util;

public final class ResumidorErrores {

    private static final int LONGITUD_MAXIMA = 1000;

    private ResumidorErrores() {
    }

    public static String resumir(Throwable error) {
        if (error == null) {
            return "";
        }
        Throwable causa = error;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        String mensaje = causa.getMessage();
        if (mensaje == null || mensaje.isBlank()) {
            mensaje = causa.getClass().getSimpleName();
        }
        return mensaje.length() <= LONGITUD_MAXIMA
                ? mensaje
                : mensaje.substring(0, LONGITUD_MAXIMA);
    }
}
