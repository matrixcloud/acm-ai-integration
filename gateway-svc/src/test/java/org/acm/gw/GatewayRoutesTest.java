package org.acm.gw;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

@SpringBootTest(properties = "eureka.client.enabled=false")
class GatewayRoutesTest {

	@Autowired
	RouteDefinitionLocator routeDefinitionLocator;

	@Test
	void exposesOneRoutePerServiceWithStripPrefixTwo() {
		List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
			.collectList()
			.block();

		assertThat(routes).extracting(RouteDefinition::getId).containsExactlyInAnyOrder(
			"customer-svc", "order-svc", "kb-svc", "customer-agent");

		assertThat(routes).allSatisfy(route -> assertThat(route.getFilters()).anySatisfy(filter -> {
			assertThat(filter.getName()).isEqualTo("StripPrefix");
			assertThat(filter.getArgs()).containsValue("2");
		}));
	}

}
