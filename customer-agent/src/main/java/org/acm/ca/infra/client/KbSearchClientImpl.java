package org.acm.ca.infra.client;

import java.util.List;
import org.acm.ca.application.port.out.KbSearchClient;
import org.acm.ca.application.port.out.KbSearchUnavailableException;
import org.acm.ca.application.rule.ReplyRulesConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP adapter for {@link KbSearchClient}, calling {@code kb-svc}'s
 * {@code POST /api/kbs/{kbNo}/search} endpoint with the {@code API-Version} header.
 */
@Component
public class KbSearchClientImpl implements KbSearchClient {

  private final RestClient restClient;

  public KbSearchClientImpl(ReplyRulesConfig config) {
    this.restClient =
        RestClient.builder()
            .baseUrl(config.kbService().baseUrl())
            .defaultHeader("API-Version", "1")
            .build();
  }

  @Override
  public List<KbChunk> search(SearchRequest request) {
    try {
      KbSearchResponse response =
          restClient
              .post()
              .uri("/api/kbs/{kbNo}/search", request.kbNo())
              .body(new KbSearchRequest(request.query(), request.topK()))
              .retrieve()
              .body(KbSearchResponse.class);
      if (response == null || response.chunks() == null) {
        return List.of();
      }
      return response.chunks().stream()
          .map(c -> new KbChunk(c.content(), c.score(), c.documentNo(), c.documentName()))
          .toList();
    } catch (KbSearchUnavailableException e) {
      throw e;
    } catch (Exception e) {
      throw new KbSearchUnavailableException("KB search failed: " + e.getMessage());
    }
  }

  record KbSearchRequest(String query, int topK) {}

  record KbSearchResponse(List<Chunk> chunks) {}

  record Chunk(String content, double score, String documentNo, String documentName) {}
}