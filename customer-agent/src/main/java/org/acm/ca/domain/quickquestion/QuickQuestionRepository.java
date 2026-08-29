package org.acm.ca.domain.quickquestion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuickQuestionRepository extends JpaRepository<QuickQuestion, Long> {
  List<QuickQuestion> findByEnabledTrueOrderBySortOrderAsc();
}
