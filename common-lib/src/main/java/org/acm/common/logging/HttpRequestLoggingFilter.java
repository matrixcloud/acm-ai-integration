package org.acm.common.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs one {@code http.in} summary line per request after the response is committed: method, path,
 * status, duration and client IP, honoring {@code X-Forwarded-For} when present.
 */
public class HttpRequestLoggingFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - start) / 1_000_000;
      log.info(
          "http.in method={} path={} status={} durationMs={} clientIp={}",
          httpRequest.getMethod(),
          httpRequest.getRequestURI(),
          httpResponse.getStatus(),
          durationMs,
          clientIp(httpRequest));
    }
  }

  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
