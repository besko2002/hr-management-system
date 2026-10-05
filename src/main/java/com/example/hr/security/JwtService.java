package com.example.hr.security;

import com.example.hr.employee.Employee;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Issues signed access tokens: subject = employee id, plus a {@code role} claim. */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Duration lifetime;

    JwtService(JwtEncoder encoder, @Value("${app.jwt.expiration:PT8H}") Duration lifetime) {
        this.encoder = encoder;
        this.lifetime = lifetime;
    }

    public String issue(Employee employee) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(employee.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .claim("role", employee.getRole().name())
                .claim("email", employee.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresInSeconds() {
        return lifetime.toSeconds();
    }
}
