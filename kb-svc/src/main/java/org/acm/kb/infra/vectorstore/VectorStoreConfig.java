package org.acm.kb.infra.vectorstore;

import org.acm.kb.infra.splitter.RecursiveCharacterTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the pgvector vector store and ETL components.
 *
 * <p>The {@code PgVectorStore} bean is auto-configured by {@code spring-ai-starter-vector-store
 * -pgvector} from the {@code spring.ai.vectorstore.pgvector.*} properties (HNSW index, cosine
 * distance, 1024 dimensions, {@code initialize-schema=true}). This class also provides the {@link
 * RecursiveCharacterTextSplitter} bean with default settings.
 */
@Configuration
public class VectorStoreConfig {

  /**
   * Provides a {@link RecursiveCharacterTextSplitter} with default chunk size 1000 and overlap 200.
   *
   * @return the splitter bean
   */
  @Bean
  public RecursiveCharacterTextSplitter recursiveCharacterTextSplitter() {
    return RecursiveCharacterTextSplitter.builder().build();
  }
}
