package com.yuno.gateway.filter;

import com.yuno.commons.mdc.MDCKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global Gateway Filter — runs on every inbound request.
 *
 * Responsibilities:
 * 1. Generate X-Correlation-Id if caller didn't supply one.
 *    This ID propagates through all downstream services via HTTP headers
 *    and Kafka message headers, enabling full trace reconstruction.
 *
 * 2. Forward X-User-Id to downstream services.
 *    In production this would be validated from a JWT token.
 *    For this assessment it's trusted from the request header.
 *
 * 3. Log request entry with routing info.
 *
 * Note: Spring Cloud Gateway is reactive (WebFlux). MDC is thread-local
 * and doesn't propagate across reactive chains naturally. We set MDC here
 * for the gateway's own log line only; downstream services set their own
 * MDC from the forwarded headers using their MDCFilter (servlet-based).
 */
@Component
@Slf4j
public class MDCGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest()
                .getHeaders()
                .getFirst(MDCKeys.HEADER_CORRELATION_ID);

        // Generate correlation ID if not present
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String userId = exchange.getRequest()
                .getHeaders()
                .getFirst(MDCKeys.HEADER_USER_ID);

        final String finalCorrelationId = correlationId;
        final String finalUserId = userId != null ? userId : "anonymous";

        // Mutate the request to add the enriched headers downstream
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(MDCKeys.HEADER_CORRELATION_ID, finalCorrelationId)
                .header(MDCKeys.HEADER_USER_ID, finalUserId)
                .build();

        log.info("[GATEWAY] {} {} | correlationId={} | userId={}",
                mutatedRequest.getMethod(),
                mutatedRequest.getURI().getPath(),
                finalCorrelationId,
                finalUserId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // Run before all other filters
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
