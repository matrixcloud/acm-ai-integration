package org.acm.kb.domain.kb;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

  List<DocumentChunk> findByDocumentId(Long documentId);

  void deleteByDocumentId(Long documentId);
}
