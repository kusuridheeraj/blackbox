package com.blackbox.gateway.routing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Catch-all controller that forwards all /api/** requests through the router.
 * By the time a request reaches here, it has already passed through:
 * 1. JwtAuthFilter (authentication)
 * 2. RateLimitFilter (rate limiting)
 *
 * This controller simply delegates to RequestRouter which handles
 * routing, circuit breaking, and metric recording.
 */
@RestController
@RequiredArgsConstructor
public class GatewayController {

    private final RequestRouter requestRouter;

    @RequestMapping("/api/**")
    public void handleRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        requestRouter.route(request, response);
    }
}
