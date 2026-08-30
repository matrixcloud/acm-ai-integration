package org.acm.kb.application.port.in;

import java.util.List;
import org.acm.kb.application.port.in.command.SearchCommand;
import org.acm.kb.domain.kb.Document;
import org.acm.kb.domain.kb.KbChunk;
import org.acm.kb.domain.kb.KnowledgeBase;
import org.springframework.web.multipart.MultipartFile;

/**
 * Application port for knowledge-base management use cases.
 *
 * <p>Returns domain objects; the adapter layer is responsible for mapping to HTTP responses.
 */
public interface KbUseCase {

  KnowledgeBase createKnowledgeBase(String name);

  List<KnowledgeBase> listKnowledgeBases();

  KnowledgeBase getKnowledgeBase(String kbNo);

  Document uploadDocument(String kbNo, MultipartFile file);

  List<Document> listDocuments(String kbNo);

  void deleteDocument(String kbNo, String docNo);

  KnowledgeBase archiveKnowledgeBase(String kbNo);

  KnowledgeBase activateKnowledgeBase(String kbNo);

  List<KbChunk> search(SearchCommand command);
}
