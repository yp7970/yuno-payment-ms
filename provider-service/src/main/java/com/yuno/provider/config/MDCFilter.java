package com.yuno.provider.config;

import com.yuno.commons.mdc.MDCKeys;
import com.yuno.commons.mdc.MDCUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class MDCFilter extends OncePerRequestFilter {

    @Value("${spring.application.name}")
    private String serviceName;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            MDCUtil.set(null,
                    req.getHeader(MDCKeys.HEADER_CORRELATION_ID),
                    req.getHeader(MDCKeys.HEADER_USER_ID),
                    serviceName);
            chain.doFilter(req, res);
        } finally {
            MDCUtil.clear();
        }
    }
}
