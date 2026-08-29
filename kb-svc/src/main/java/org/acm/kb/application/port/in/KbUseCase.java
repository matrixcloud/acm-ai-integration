package org.acm.kb.application.port.in;

import java.util.List;
import org.acm.kb.application.port.in.command.SearchCommand;
import org.acm.kb.domain.kb.Document;
import org.acm.kb.domain.kb.KnowledgeBase;
import org.acm.kb.application.port.out.KbSearchClient.KbChunk;
import org.springframework.web.multipart.MultipartFile;

/**
 * Application port for knowledge-base management use cases.
 *
 * <p>Returns domain objects; the adapter layer maps to HTTP responses.
 */
public interface KbUseCase {

  /**
   * Creates a new knowledge base.
   *
   * @param name knowledge base name
   * @return the created knowledge base
   */
  KnowledgeBase createKnowledgeBase(String name);

  /**
   * Lists all knowledge bases.
   *
   * @return all knowledge bases
   */
  List<KnowledgeBase> listKnowledgeBases();

  /**
   * Retrieves a knowledge base by its number.
   *
   * @param kbNo knowledge base number
   * @return the knowledge base
   */
  KnowledgeBase getKnowledgeBase(String kbNo);

  /**
   * Uploads a document to a knowledge base.
   *
   * @param kbNo knowledge base number
   * @param file uploaded text or Markdown file
   * @return the created document
   */
  Document uploadDocument(String kbNo, MultipartFile file);

  /**
   * Lists documents in a knowledge base.
   *
   * @param kbNo knowledge base number
   * @return documents in the knowledge base
   */
  List<Document> listDocuments(String kbNo);

  /**
   * Deletes a document from a knowledge base.
   *
   * @param kbNo knowledge base number
   * @param docNo document number
   */
  void deleteDocument(String kbNo, String docNo);

  /**
   * Archives a knowledge base.
   *
   * @param kbNo knowledge base number
   * @return the archived knowledge base
   */
  KnowledgeBase archiveKnowledgeBase(String kbNo);

  /**
   * Reactivates a knowledge base.
   *
   * @param kbNo knowledge base number
   * @return the reactivated knowledge base
   */
  KnowledgeBase activateKnowledgeBase(String kbNo);

  /**
   * Performs a similarity search against a knowledge base.
   *
   * @param command search command with kb number, query, and top-K
   * @return matching chunks
   */
  List<KbChunk> search(SearchCommand command);
}
