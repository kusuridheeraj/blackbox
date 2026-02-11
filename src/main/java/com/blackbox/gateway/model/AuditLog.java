package com.blackbox.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Audit log entity.
 * Records every significant gateway decision for post-incident analysis.
 *
 * What gets logged:
 * - Rate limit adjustments (tighten/relax)
 * - Circuit breaker state changes
 * - Authentication failures
 * - Fallback activations (Redis down)
 *
 * What does NOT get logged here (too high volume):
 * - Individual request logs (use access logs / Prometheus for that)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventType; // RATE_LIMIT_ADJUST, CIRCUIT_BREAK, AUTH_FAILURE, FALLBACK

    @Column(nullable = false)
    private String source; // Which component created the event

    private String clientId; // Related client (if applicable)

    private String routeId; // Related route (if applicable)

    @Column(columnDefinition = "TEXT")
    private String details; // JSON details of the event

    @Column(nullable = false)
    @Builder.Default
    private Instant timestamp = Instant.now();
}
