package com.blackbox.gateway.exception;

import com.blackbox.gateway.model.GatewayErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global error handler.
 * Ensures every error from the gateway follows the same response format.
 * No stack traces leak to clients.
 */
@Slf4j
@RestControllerAdvice
public class GatewayErrorHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GatewayErrorResponse> handleGenericError(Exception e) {
        log.error("Unhandled gateway error: {}", e.getMessage(), e);

        GatewayErrorResponse error = GatewayErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
