package org.tudai.ticketya.usuarios.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.contract.usuarios.model.TokenResponse;
import org.tudai.ticketya.usuarios.domain.Usuario;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final String issuer;

    public JwtService(JwtEncoder encoder,
                      @Value("${ticketya.jwt.ttl:PT1H}") Duration ttl,
                      @Value("${ticketya.jwt.issuer:ticketya-usuarios}") String issuer) {
        this.encoder = encoder;
        this.ttl = ttl;
        this.issuer = issuer;
    }

    public TokenResponse emitir(Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(ttl))
                .claim("roles", usuario.getRoles().stream().map(Enum::name).sorted().toList())
                .build();
        String token = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        TokenResponse respuesta = new TokenResponse();
        respuesta.setAccessToken(token);
        respuesta.setTokenType("Bearer");
        respuesta.setExpiresIn(ttl.toSeconds());
        return respuesta;
    }
}
