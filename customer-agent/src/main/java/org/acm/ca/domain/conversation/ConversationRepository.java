package org.acm.ca.domain.conversation;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

  @EntityGraph(attributePaths = {"messages", "feedback"})
  Optional<Conversation> findByConversationNo(String conversationNo);

  // feedback is an eager reverse-side OneToOne by default; fetch it in the same query
  // so list/search pages do not issue one extra SELECT per row (N+1).
  @EntityGraph(attributePaths = "feedback")
  Page<Conversation> findByCustomerId(String customerId, Pageable pageable);

  @EntityGraph(attributePaths = "feedback")
  Page<Conversation> findByCustomerIdAndStatus(
      String customerId, ConversationStatus status, Pageable pageable);
}
