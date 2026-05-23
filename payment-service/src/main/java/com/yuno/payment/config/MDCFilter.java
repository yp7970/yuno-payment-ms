package com.yuno.payment.config;

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
 * Servlet filter that populates MDC at the start of every HTTP request
 * and clears it at the end — ensuring no MDC leakage across requests
 * in a thread-pool environment.
 *
 * After this filter runs, every log line in the request thread carries:
 *   [trxId=<paymentId>] [corrId=<X-Correlation-Id>] [userId=<X-User-Id>]
 *
 * The transactionId is initially empty for POST /payments (set later in
 * PaymentService once the UUID is generated and saved).
 * For GET /payments/{id} it is extracted from the path variable.
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

            // Pass correlation ID back in response headers for client tracing
            if (correlationId != null) {
                response.setHeader(MDCKeys.HEADER_CORRELATION_ID, correlationId);
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear — thread may be reused for a different request
            MDCUtil.clear();
        }
    }
}
