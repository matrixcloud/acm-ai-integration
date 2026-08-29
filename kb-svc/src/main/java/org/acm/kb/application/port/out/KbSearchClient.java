package org.acm.kb.application.port.out;

import java.util.List;

/**
 * Outbound search port for knowledge-base retrieval, consumed by {@code customer-agent} via a
 * mock adapter in the demo phase.
 *
 * <p>This port exposes similarity search to external callers; {@code kb-svc}'s own application
 * service uses {@code VectorStore} directly.
 */
public interface KbSearchClient {

  /**
   * Searches the knowledge base for chunks similar to the query.
   *
   * @param request search request carrying kb number, query text, and top-K
   * @return matching chunks with content, score, and source document info
   */
  List<KbChunk> search(SearchRequest request);

  /** Search request parameters. */
  record SearchRequest(String kbNo, String query, int topK) {}

  /** A single retrieved chunk. */
  record KbChunk(String content, double score, String documentNo, String documentName) {}
}
