package org.acm.gw;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.http.HttpStatus;

@SpringBootTest(properties = "eureka.client.enabled=false")
class GatewayRoutesTest {

  private static final List<String> GET_ROUTE_IDS =
      List.of("customer-agent-get", "order-svc-get", "kb-svc-get");
  private static final List<String> WRITE_ROUTE_IDS =
      List.of("customer-agent-write", "order-svc-write", "kb-svc-write");
  private static final List<String> SSE_ROUTE_IDS = List.of("customer-agent-sse");
  private static final List<String> OTHER_ROUTE_IDS =
      List.of("customer-agent-other", "order-svc-other", "kb-svc-other");

  @Autowired RouteDefinitionLocator routeDefinitionLocator;

  @Test
  void exposesOneRoutePerMethodPerServiceWithStripPrefixTwo() {
    List<RouteDefinition> routes = routes();

    assertThat(routes)
        .extracting(RouteDefinition::getId)
        .containsExactlyInAnyOrder(
            "customer-agent-get",
            "customer-agent-write",
            "customer-agent-sse",
            "customer-agent-other",
            "order-svc-get",
            "order-svc-write",
            "order-svc-other",
            "kb-svc-get",
            "kb-svc-write",
            "kb-svc-other");

    assertThat(routes)
        .allSatisfy(
            route ->
                assertThat(route.getFilters())
                    .anySatisfy(
                        filter -> {
                          assertThat(filter.getName()).isEqualTo("StripPrefix");
                          assertThat(filter.getArgs()).containsValue("2");
                        }));
  }

  @Test
  void getRoutesRetryTransientStatusesOnceAndAreCircuitBreakerProtected() {
    for (RouteDefinition route : routesById(GET_ROUTE_IDS)) {
      Map<String, String> retryArgs = filterArgs(route, "Retry");
      assertThat(retryArgs)
          .as("%s retry args", route.getId())
          .containsEntry("retries", "1")
          .containsEntry("methods", "GET")
          // 显式关闭默认的 series=[SERVER_ERROR]：500 是下游缺陷，不重试
          .containsEntry("series", "")
          .containsEntry("statuses", "BAD_GATEWAY,SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT");
      assertThat(route.getFilters())
          .as("%s circuit breaker", route.getId())
          .anySatisfy(filter -> assertThat(filter.getName()).isEqualTo("CircuitBreaker"));
    }
  }

  @Test
  void writeRoutesAreCircuitBreakerProtectedWithoutRetry() {
    for (RouteDefinition route : routesById(WRITE_ROUTE_IDS)) {
      assertThat(route.getFilters())
          .as("%s circuit breaker", route.getId())
          .anySatisfy(filter -> assertThat(filter.getName()).isEqualTo("CircuitBreaker"));
      assertThat(filterArgs(route, "Retry")).as("%s must not retry writes", route.getId()).isNull();
    }
  }

  @Test
  void sseRoutesCarryNeitherRetryNorCircuitBreaker() {
    for (RouteDefinition route : routesById(SSE_ROUTE_IDS)) {
      assertThat(filterNames(route)).as("%s filters", route.getId()).containsOnly("StripPrefix");
      assertThat(route.getPredicates())
          .as("%s must be POST only", route.getId())
          .anySatisfy(p -> assertThat(p.getName()).isEqualTo("Method"));
    }
  }

  @Test
  void otherMethodRoutesStayPlainPassThrough() {
    for (RouteDefinition route : routesById(OTHER_ROUTE_IDS)) {
      assertThat(filterNames(route))
          .as("%s must not retry or trip on management traffic", route.getId())
          .containsOnly("StripPrefix");
      assertThat(route.getPredicates())
          .as("%s must not be method restricted (catch-all)", route.getId())
          .noneSatisfy(p -> assertThat(p.getName()).isEqualTo("Method"));
    }
  }

  @Test
  void retryFilterEmptySeriesBindsToNoSeriesMatching() {
    // filter 配置在首个请求时才绑定；这里用与 RouteDefinitionRouteLocator 相同的 Binder 语义
    // 验证 series: '' 确实覆盖默认的 [SERVER_ERROR]，而不是被当作“未配置”忽略
    RetryGatewayFilterFactory.RetryConfig config = new RetryGatewayFilterFactory.RetryConfig();
    MapConfigurationPropertySource source =
        new MapConfigurationPropertySource(Map.of("series", "", "statuses", "BAD_GATEWAY"));
    new Binder(source).bind("", Bindable.ofInstance(config));

    assertThat(config.getSeries()).isEmpty();
    assertThat(config.getStatuses()).containsExactly(HttpStatus.BAD_GATEWAY);
  }

  private List<RouteDefinition> routes() {
    return routeDefinitionLocator.getRouteDefinitions().collectList().block();
  }

  private List<RouteDefinition> routesById(List<String> ids) {
    return routes().stream()
        .filter(route -> ids.contains(route.getId()))
        .collect(Collectors.toList());
  }

  private static List<String> filterNames(RouteDefinition route) {
    return route.getFilters().stream().map(f -> f.getName()).toList();
  }

  private static Map<String, String> filterArgs(RouteDefinition route, String filterName) {
    return route.getFilters().stream()
        .filter(filter -> filter.getName().equals(filterName))
        .findFirst()
        .map(filter -> filter.getArgs())
        .orElse(null);
  }
}
