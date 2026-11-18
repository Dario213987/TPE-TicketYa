package org.tudai.ticketya.notificaciones.service.destinatarios;

import org.springframework.stereotype.Service;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DestinatariosResolverRegistry {

    private static final Set<MotivoNotificacion> MOTIVOS_MASIVOS = EnumSet.of(
            MotivoNotificacion.CANCELACION,
            MotivoNotificacion.EDICION,
            MotivoNotificacion.PUBLICACION_ARTISTA,
            MotivoNotificacion.SUSPENSION);

    private final Map<MotivoNotificacion, DestinatariosResolver> resolvers;

    public DestinatariosResolverRegistry(List<DestinatariosResolver> resolvers) {
        Map<MotivoNotificacion, DestinatariosResolver> porMotivo = new EnumMap<>(MotivoNotificacion.class);
        for (DestinatariosResolver resolver : resolvers) {
            for (MotivoNotificacion motivo : resolver.motivos()) {
                if (porMotivo.putIfAbsent(motivo, resolver) != null) {
                    throw new IllegalStateException("Hay mas de un resolvedor para el motivo " + motivo);
                }
            }
        }
        for (MotivoNotificacion motivo : MOTIVOS_MASIVOS) {
            if (!porMotivo.containsKey(motivo)) {
                throw new IllegalStateException("No hay resolvedor para el motivo " + motivo);
            }
        }
        this.resolvers = porMotivo;
    }

    public DestinatariosResolver resolver(MotivoNotificacion motivo) {
        DestinatariosResolver resolver = resolvers.get(motivo);
        if (resolver == null) {
            throw new IllegalArgumentException("No hay resolvedor de destinatarios para el motivo " + motivo);
        }
        return resolver;
    }
}
