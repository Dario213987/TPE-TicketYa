package org.tudai.ticketya.tickets.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Permite leer respuestas paginadas de otros servicios con los clientes Feign generados.
 * <p>
 * Los contratos mapean la página a {@link PagedModel} de Spring Data, que Jackson no sabe
 * construir (no tiene un constructor para deserializar). Este módulo arma el {@code PagedModel}
 * a partir de {@code content} y {@code page}. Los elementos quedan como los entrega Jackson
 * (mapas), y quien los usa los convierte al DTO del contrato (TKY-87).
 */
@Configuration
public class PagedModelJacksonConfig {

    @Bean
    public Module pagedModelModule() {
        return new SimpleModule("PagedModelModule")
                .addDeserializer(PagedModel.class, new PagedModelDeserializer());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static class PagedModelDeserializer extends JsonDeserializer<PagedModel> {

        @Override
        public PagedModel deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode raiz = parser.getCodec().readTree(parser);
            List<Object> contenido = new ArrayList<>();
            JsonNode content = raiz.path("content");
            if (content.isArray()) {
                for (JsonNode elemento : content) {
                    contenido.add(parser.getCodec().treeToValue(elemento, Object.class));
                }
            }

            JsonNode page = raiz.path("page");
            int size = page.path("size").asInt(contenido.size());
            int number = page.path("number").asInt(0);
            long total = page.path("totalElements").asLong(contenido.size());
            Pageable pageable = size > 0 ? PageRequest.of(number, size) : Pageable.unpaged();
            return new PagedModel(new PageImpl(contenido, pageable, total));
        }
    }
}
