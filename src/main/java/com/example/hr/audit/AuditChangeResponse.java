package com.example.hr.audit;

import java.time.Instant;

/** One changed field at one Envers revision. */
public record AuditChangeResponse(
        int revision,
        Instant at,
        String changedBy,
        String field,
        String before,
        String after) {
}
