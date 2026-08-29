package org.acm.cs.domain.conversation;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

  @EntityGraph(attributePaths = {"messages", "feedback"})
  Optional<Conversation> findByConversationNo(String conversationNo);

  Page<Conversation> findByCustomerId(String customerId, Pageable pageable);

  Page<Conversation> findByCustomerIdAndStatus(
      String customerId, ConversationStatus status, Pageable pageable);
}
