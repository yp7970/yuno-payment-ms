package com.yuno.idempotency.config;

import com.yuno.commons.mdc.MDCKeys;
import com.yuno.commons.mdc.MDCUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that populates MDC from incoming request headers.
 *
 * idempotency-service is called synchronously by payment-service via Feign.
 * The Feign call carries X-Correlation-Id and X-User-Id headers forwarded
 * from the original HTTP request. This filter restores MDC from those headers
 * so every log line in this service is traceable to the originating payment.
 *
 * The transactionId (paymentId) is not set here — it is not forwarded in the
 * Feign call headers. It is embedded in the request path (/idempotency/{key})
 * and in the stored response body, so it appears in log messages explicitly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class MDCFilter extends OncePerRequestFilter {

    @Value("${spring.application.name}")
    private String serviceName;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String correlationId = request.getHeader(MDCKeys.HEADER_CORRELATION_ID);
            String userId        = request.getHeader(MDCKeys.HEADER_USER_ID);

            MDCUtil.set(null, correlationId, userId, serviceName);

            // Echo correlation ID back in response for client tracing
            if (correlationId != null) {
                response.setHeader(MDCKeys.HEADER_CORRELATION_ID, correlationId);
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear — thread-pool reuse protection
            MDCUtil.clear();
        }
    }
}
