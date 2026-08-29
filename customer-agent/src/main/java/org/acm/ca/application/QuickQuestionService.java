package org.acm.ca.application;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.acm.ca.client.CustomerSvcClient;
import org.acm.ca.client.QuickQuestionView;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuickQuestionService {

	private final CustomerSvcClient customerSvcClient;

	@Retry(name = "customer-svc")
	@CircuitBreaker(name = "customer-svc")
	public List<QuickQuestionView> list() {
		return customerSvcClient.getQuickQuestions();
	}
}
