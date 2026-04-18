package com.hackathon.chat.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forwards SPA deep links (e.g. /rooms/&lt;uuid&gt;) to the Angular index.html so that
 * a direct browser navigation or page reload lands on the app instead of Spring's 404.
 * API and WebSocket paths, and any request that looks like a static asset (contains a
 * dot), fall through to the normal handler chain.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpaFallbackFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (shouldForward(request)) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean shouldForward(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null || path.equals("/") || path.contains(".")) {
            return false;
        }
        return !path.startsWith("/api/") && !path.startsWith("/ws");
    }
}
