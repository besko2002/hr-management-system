package com.example.hr.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Stamps every Envers revision with the current actor. Prefer the JWT {@code email} claim
 * (see {@link com.example.hr.security.JwtService}); fall back to the subject (employee id).
 * No security context → {@value #SYSTEM}.
 */
public class AuditRevisionListener implements RevisionListener {

    public static final String SYSTEM = "system";

    @Override
    public void newRevision(Object revisionEntity) {
        ((AuditRevision) revisionEntity).setChangedBy(resolveActor());
    }

    static String resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM;
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String email = jwt.getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return SYSTEM;
        }
        return name;
    }
}
