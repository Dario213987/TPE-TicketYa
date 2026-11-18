package org.tudai.ticketya.preferencias.util;

import java.text.Normalizer;
import java.util.Locale;

public final class NormalizadorTexto {

    private NormalizadorTexto() {
    }

    public static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
