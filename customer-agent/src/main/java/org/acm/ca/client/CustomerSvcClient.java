package org.acm.ca.client;

import java.util.List;
import org.springframework.web.service.annotation.GetExchange;

public interface CustomerSvcClient {

	@GetExchange("/quick-questions")
	List<QuickQuestionView> getQuickQuestions();
}
