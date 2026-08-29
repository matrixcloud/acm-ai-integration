package org.acm.ca.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.acm.ca.client.CustomerSvcClient;
import org.acm.ca.client.QuickQuestionView;
import org.junit.jupiter.api.Test;

class QuickQuestionServiceTest {

	private final CustomerSvcClient client = mock(CustomerSvcClient.class);
	private final QuickQuestionService service = new QuickQuestionService(client);

	@Test
	void delegatesToList() {
		QuickQuestionView view = new QuickQuestionView(1L, 1, "怎么查订单?");
		when(client.getQuickQuestions()).thenReturn(List.of(view));

		assertThat(service.list()).containsExactly(view);
	}

	@Test
	void propagatesFailureWithoutSwallowing() {
		when(client.getQuickQuestions()).thenThrow(new IllegalStateException("customer-svc unavailable"));

		assertThatThrownBy(service::list)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("customer-svc unavailable");

		verify(client, times(1)).getQuickQuestions();
	}

}
