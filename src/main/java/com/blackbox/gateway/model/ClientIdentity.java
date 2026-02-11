package com.blackbox.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a client identity extracted from JWT claims.
 * Used for per-client rate limiting and audit logging.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientIdentity {

    /** Unique client identifier (from JWT 'sub' claim) */
    private String clientId;

    /** Client tier for rate limit lookup (STANDARD, PREMIUM, INTERNAL) */
    private String tier;

    /** Human-readable name for audit logs */
    private String name;

    /** Request attribute key used to pass identity through filter chain */
    public static final String REQUEST_ATTRIBUTE = "blackbox.client.identity";
}
