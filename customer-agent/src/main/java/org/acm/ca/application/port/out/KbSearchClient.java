package org.acm.ca.application.port.out;

import java.util.List;

/**
 * Outbound port for knowledge-base retrieval, realized by an HTTP adapter calling {@code kb-svc}.
 */
public interface KbSearchClient {

  List<KbChunk> search(SearchRequest request);

  record SearchRequest(String kbNo, String query, int topK) {}

  record KbChunk(String content, double score, String documentNo, String documentName) {}
}
