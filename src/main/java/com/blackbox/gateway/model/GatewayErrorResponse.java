package com.blackbox.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Wraps gateway error responses with consistent structure.
 * Every 4xx/5xx from the gateway follows this format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayErrorResponse {

    private int status;
    private String error;
    private String message;
    private String path;

    @Builder.Default
    private String timestamp = Instant.now().toString();

    /** Present only on 429 responses */
    private Long retryAfterSeconds;
}
