package org.acm.ca.infra.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.List;
import org.acm.ca.application.port.out.KbSearchClient;
import org.acm.ca.application.port.out.KbSearchUnavailableException;
import org.acm.ca.application.port.out.TransientKbSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/**
 * HTTP adapter for {@link KbSearchClient}, calling {@code kb-svc}'s search endpoint through the
 * {@code kb-svc} HTTP service group. The call is a POST carrying read-only search semantics, so a
 * single transient-failure retry is allowed; empty or malformed responses fail fast instead of
 * returning a silent empty list.
 */
@Component
public class KbSearchClientImpl implements KbSearchClient {

  private static final Logger log = LoggerFactory.getLogger(KbSearchClientImpl.class);

  static final String API_VERSION = "1";

  private final KbServiceHttpClient kbServiceHttpClient;

  public KbSearchClientImpl(KbServiceHttpClient kbServiceHttpClient) {
    this.kbServiceHttpClient = kbServiceHttpClient;
  }

  @Retryable(
      includes = TransientKbSearchException.class,
      maxRetries = 1,
      delay = 100,
      multiplier = 2,
      maxDelay = 500,
      jitter = 50,
      timeout = 8000)
  @Override
  public List<KbChunk> search(SearchRequest request) {
    long start = System.nanoTime();
    try {
      KbSearchResponse response =
          kbServiceHttpClient.search(
              API_VERSION, request.kbNo(), new KbSearchRequest(request.query(), request.topK()));
      if (response == null) {
        throw new KbSearchUnavailableException("KB search response body must not be null");
      }
      if (response.chunks() == null) {
        throw new KbSearchUnavailableException("KB search response chunks must not be null");
      }
      List<KbChunk> chunks =
          response.chunks().stream()
              .map(c -> new KbChunk(c.content(), c.score(), c.documentNo(), c.documentName()))
              .toList();
      log.info(
          "http.out service=kb-svc op=search kbNo={} topK={} chunks={} durationMs={}",
          request.kbNo(),
          request.topK(),
          chunks.size(),
          (System.nanoTime() - start) / 1_000_000);
      return chunks;
    } catch (KbSearchUnavailableException e) {
      throw e;
    } catch (NoFallbackAvailableException e) {
      throw unwrapTransportFailure(e);
    } catch (HttpStatusCodeException e) {
      throw toUnavailable(e);
    } catch (ResourceAccessException e) {
      throw new TransientKbSearchException("KB service connection failed", e);
    } catch (CallNotPermittedException e) {
      throw new KbSearchUnavailableException("KB service circuit breaker is open", e);
    } catch (Exception e) {
      throw new KbSearchUnavailableException("KB search response contract violated", e);
    }
  }

  /**
   * With the circuit breaker decorator on the HTTP service group, transport failures surface
   * wrapped in {@link NoFallbackAvailableException}; unwrap them so the retry policy sees the
   * original failure kind.
   */
  private static KbSearchUnavailableException unwrapTransportFailure(
      NoFallbackAvailableException e) {
    Throwable cause = e.getCause() == null ? e : e.getCause();
    if (cause instanceof CallNotPermittedException) {
      return new KbSearchUnavailableException("KB service circuit breaker is open", cause);
    }
    if (cause instanceof HttpStatusCodeException http) {
      return toUnavailable(http);
    }
    if (cause instanceof ResourceAccessException io) {
      return new TransientKbSearchException("KB service connection failed", io);
    }
    return new KbSearchUnavailableException("KB search response contract violated", cause);
  }

  private static KbSearchUnavailableException toUnavailable(HttpStatusCodeException e) {
    return switch (e.getStatusCode().value()) {
      case 429, 502, 503, 504 ->
          new TransientKbSearchException(
              "KB service transient failure: HTTP %s".formatted(e.getStatusCode()), e);
      default ->
          new KbSearchUnavailableException(
              "Unexpected kb-svc response status: HTTP %s".formatted(e.getStatusCode()), e);
    };
  }

  public record KbSearchRequest(String query, int topK) {}

  public record KbSearchResponse(List<Chunk> chunks) {}

  public record Chunk(String content, double score, String documentNo, String documentName) {}
}
