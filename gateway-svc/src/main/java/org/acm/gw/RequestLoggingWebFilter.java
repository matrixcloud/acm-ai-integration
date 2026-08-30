package org.acm.gw;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Logs one {@code http.in} summary line per gateway request after the response terminates: method,
 * path, status, duration and client IP, honoring {@code X-Forwarded-For} when present.
 */
@Component
public class RequestLoggingWebFilter implements WebFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingWebFilter.class);

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    long start = System.nanoTime();
    return chain
        .filter(exchange)
        .doFinally(
            signalType -> {
              long durationMs = (System.nanoTime() - start) / 1_000_000;
              HttpStatusCode status = exchange.getResponse().getStatusCode();
              log.info(
                  "http.in method={} path={} status={} durationMs={} clientIp={}",
                  exchange.getRequest().getMethod(),
                  exchange.getRequest().getPath().value(),
                  status == null ? 0 : status.value(),
                  durationMs,
                  clientIp(exchange.getRequest()));
            });
  }

  private static String clientIp(ServerHttpRequest request) {
    List<String> forwarded = request.getHeaders().get("X-Forwarded-For");
    if (forwarded != null && !forwarded.isEmpty()) {
      return forwarded.get(0).split(",")[0].trim();
    }
    return request.getRemoteAddress() == null
        ? "unknown"
        : request.getRemoteAddress().getAddress().getHostAddress();
  }
}
