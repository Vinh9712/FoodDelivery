package com.fooddelivery.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Assigns/propagates a correlation id (X-Request-ID) for every request and
 * logs the request/response with timing. Runs first in the global filter chain.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Reuse an inbound correlation id if present, otherwise generate one.
        String correlationId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().substring(0, 12);
        }

        // Forward the id downstream and echo it back to the client.
        final String requestId = correlationId;
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.header(REQUEST_ID_HEADER, requestId))
                .build();
        mutated.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        long start = System.currentTimeMillis();
        log.info("[{}] --> {} {}", requestId, request.getMethod(), request.getURI().getRawPath());

        return chain.filter(mutated).doFinally(signal -> {
            long took = System.currentTimeMillis() - start;
            log.info("[{}] <-- {} {} ({} ms)",
                    requestId,
                    mutated.getResponse().getStatusCode(),
                    request.getURI().getRawPath(),
                    took);
        });
    }

    @Override
    public int getOrder() {
        // Run before the routing filter so the id is present for downstream services.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
