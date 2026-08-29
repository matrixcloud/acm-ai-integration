package org.acm.kb.domain.kb;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

  Optional<Document> findByDocumentNo(String documentNo);

  List<Document> findByKbId(Long kbId);
}
