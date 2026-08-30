package org.acm.kb.domain.kb;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, String> {

  Optional<KnowledgeBase> findByKbNo(String kbNo);
}
