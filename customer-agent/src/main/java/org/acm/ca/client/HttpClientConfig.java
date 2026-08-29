package org.acm.ca.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = "customer-svc", types = CustomerSvcClient.class)
public class HttpClientConfig {

	private static final String REQUIRED_BASE_URL = "lb://customer-svc";

	public HttpClientConfig(
			@Value("${spring.http.serviceclient.customer-svc.base-url:}") String baseUrl) {
		if (!REQUIRED_BASE_URL.equals(baseUrl)) {
			throw new IllegalStateException(
					"spring.http.serviceclient.customer-svc.base-url must be " + REQUIRED_BASE_URL
							+ " but is: '" + baseUrl + "'");
		}
	}

}
