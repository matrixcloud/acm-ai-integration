package org.acm.ca.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.List;
import org.acm.ca.client.CustomerSvcClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "eureka.client.enabled=false")
class ResilienceWiringTest {

	@Autowired
	QuickQuestionService quickQuestionService;

	@Autowired
	RetryRegistry retryRegistry;

	@Autowired
	CircuitBreakerRegistry circuitBreakerRegistry;

	@MockitoBean
	CustomerSvcClient customerSvcClient;

	@Test
	void annotationsCreateInstancesNamedCustomerSvcWithYamlConfig() {
		when(customerSvcClient.getQuickQuestions()).thenReturn(List.of());
		quickQuestionService.list();

		assertThat(retryRegistry.getAllRetries()).extracting(Retry::getName).contains("customer-svc");
		assertThat(retryRegistry.retry("customer-svc").getRetryConfig().getMaxAttempts()).isEqualTo(3);

		assertThat(circuitBreakerRegistry.getAllCircuitBreakers())
			.extracting(CircuitBreaker::getName)
			.contains("customer-svc");
		assertThat(circuitBreakerRegistry.circuitBreaker("customer-svc")
			.getCircuitBreakerConfig()
			.getFailureRateThreshold()).isEqualTo(50f);
	}

}
