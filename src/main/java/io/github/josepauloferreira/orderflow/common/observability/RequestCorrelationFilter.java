package io.github.josepauloferreira.orderflow.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds one safe request identifier to every response and to SLF4J MDC.
 * Never logs credentials, request bodies, or a raw untrusted header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestCorrelationFilter extends OncePerRequestFilter {

  private static final String HEADER = "X-Request-ID";
  private static final String MDC_KEY = "requestId";
  private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String provided = request.getHeader(HEADER);
    String correlationId =
        provided != null && SAFE_ID.matcher(provided).matches()
            ? provided
            : UUID.randomUUID().toString();

    String previous = MDC.get(MDC_KEY);
    response.setHeader(HEADER, correlationId);
    MDC.put(MDC_KEY, correlationId);
    long started = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      if (!request.getRequestURI().startsWith("/actuator/health")) {
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        log.atInfo()
            .addKeyValue("http.request.method", request.getMethod())
            .addKeyValue("http.response.status_code", response.getStatus())
            .addKeyValue("duration_ms", elapsedMs)
            .log("HTTP request completed");
      }
      if (previous == null) {
        MDC.remove(MDC_KEY);
      } else {
        MDC.put(MDC_KEY, previous);
      }
    }
  }
}
