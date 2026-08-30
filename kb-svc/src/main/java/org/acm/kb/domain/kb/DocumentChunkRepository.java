package org.acm.kb.domain.kb;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, String> {

  List<DocumentChunk> findByDocumentId(String documentId);

  void deleteByDocumentId(String documentId);
}
