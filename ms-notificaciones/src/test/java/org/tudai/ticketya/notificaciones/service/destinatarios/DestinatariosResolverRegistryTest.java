package org.tudai.ticketya.notificaciones.service.destinatarios;

import org.junit.jupiter.api.Test;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DestinatariosResolverRegistryTest {

    @Test
    void registraResolversPorMotivo() {
        DestinatariosResolver compradores = resolverCon(MotivoNotificacion.CANCELACION,
                MotivoNotificacion.EDICION, MotivoNotificacion.SUSPENSION);
        DestinatariosResolver preferencias = resolverCon(MotivoNotificacion.PUBLICACION_ARTISTA);

        DestinatariosResolverRegistry registry =
                new DestinatariosResolverRegistry(List.of(compradores, preferencias));

        assertThat(registry.resolver(MotivoNotificacion.CANCELACION)).isSameAs(compradores);
        assertThat(registry.resolver(MotivoNotificacion.PUBLICACION_ARTISTA)).isSameAs(preferencias);
    }

    @Test
    void duplicadoFalla() {
        DestinatariosResolver primero = resolverCon(MotivoNotificacion.CANCELACION);
        DestinatariosResolver segundo = resolverCon(MotivoNotificacion.CANCELACION);

        assertThatThrownBy(() -> new DestinatariosResolverRegistry(List.of(primero, segundo)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mas de un resolvedor");
    }

    @Test
    void motivoMasivoSinResolverFalla() {
        DestinatariosResolver resolver = resolverCon(MotivoNotificacion.CANCELACION);

        assertThatThrownBy(() -> new DestinatariosResolverRegistry(List.of(resolver)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No hay resolvedor");
    }

    @Test
    void motivoNoMasivoNoRequiereResolver() {
        DestinatariosResolverRegistry registry = new DestinatariosResolverRegistry(List.of(
                resolverCon(MotivoNotificacion.CANCELACION, MotivoNotificacion.EDICION,
                        MotivoNotificacion.SUSPENSION),
                resolverCon(MotivoNotificacion.PUBLICACION_ARTISTA)));

        assertThatThrownBy(() -> registry.resolver(MotivoNotificacion.CONFIRMACION_COMPRA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No hay resolvedor");
    }

    private DestinatariosResolver resolverCon(MotivoNotificacion... motivos) {
        DestinatariosResolver resolver = mock(DestinatariosResolver.class);
        when(resolver.motivos()).thenReturn(Set.of(motivos));
        return resolver;
    }
}
