package org.tudai.ticketya.eventos.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoEventoContractTest {

    private static final Pattern CHECK_ESTADO = Pattern.compile(
            "CHECK\\s*\\(\\s*estado\\s+IN\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALOR_ENTRE_COMILLAS = Pattern.compile("'([^']+)'");

    @Test
    void losEstadosDelContratoCoincidenConLaMigracionYNoIncluyenConfirmado() throws IOException {
        Set<String> estadosContrato = leerEstadosDelContrato();
        List<Set<String>> estadosMigracion = leerEstadosDeLaMigracion();

        assertThat(estadosContrato)
                .hasSize(6)
                .doesNotContain("CONFIRMADO");
        assertThat(estadosMigracion).hasSize(2);
        estadosMigracion.forEach(estados -> assertThat(estados).isEqualTo(estadosContrato));
    }

    private Set<String> leerEstadosDelContrato() throws IOException {
        String openapi = Files.readString(resolver(
                "../contracts/eventos-contract/src/main/resources/openapi.yaml",
                "contracts/eventos-contract/src/main/resources/openapi.yaml"));

        int inicioBloque = openapi.indexOf("EstadoEvento:");
        assertThat(inicioBloque).isGreaterThanOrEqualTo(0);

        int inicioEnum = openapi.indexOf("enum:", inicioBloque);
        int inicioLista = openapi.indexOf('[', inicioEnum);
        int finLista = openapi.indexOf(']', inicioLista);

        assertThat(inicioEnum).isGreaterThan(inicioBloque);
        assertThat(inicioLista).isGreaterThan(inicioEnum);
        assertThat(finLista).isGreaterThan(inicioLista);

        return Arrays.stream(openapi.substring(inicioLista + 1, finLista).split(","))
                .map(String::trim)
                .filter(valor -> !valor.isEmpty())
                .collect(Collectors.toSet());
    }

    private List<Set<String>> leerEstadosDeLaMigracion() throws IOException {
        String migracion = Files.readString(resolver(
                "src/main/resources/db/changelog/00009-eliminar-estado-confirmado.xml",
                "ms-eventos/src/main/resources/db/changelog/00009-eliminar-estado-confirmado.xml"));

        Matcher checks = CHECK_ESTADO.matcher(migracion);
        List<Set<String>> estados = new ArrayList<>();

        while (checks.find()) {
            Matcher valores = VALOR_ENTRE_COMILLAS.matcher(checks.group(1));
            Set<String> estadosCheck = new HashSet<>();
            while (valores.find()) {
                estadosCheck.add(valores.group(1));
            }
            estados.add(estadosCheck);
        }

        return estados;
    }

    private Path resolver(String... candidatos) {
        for (String candidato : candidatos) {
            Path path = Path.of(candidato);
            if (Files.exists(path)) {
                return path;
            }
        }
        throw new IllegalStateException("No se encontró ninguno de los archivos: " + String.join(", ", candidatos));
    }
}
