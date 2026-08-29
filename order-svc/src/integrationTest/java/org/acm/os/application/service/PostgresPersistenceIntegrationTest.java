package org.acm.os.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.acm.os.application.idempotency.IdempotencyRecordRepository;
import org.acm.os.domain.order.Order;
import org.acm.os.domain.order.OrderItem;
import org.acm.os.domain.order.OrderRepository;
import org.acm.os.infra.AuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  AuditingConfig.class,
  IdempotencyService.class,
  PostgresPersistenceIntegrationTest.JacksonTestConfiguration.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresPersistenceIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private OrderRepository orderRepository;
  @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
  @Autowired private IdempotencyService idempotencyService;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    orderRepository.deleteAll();
    idempotencyRecordRepository.deleteAll();
  }

  @Test
  void flywayCreatesExpectedSchema() {
    Integer auditColumnMigration =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '2' and success",
            Integer.class);

    assertThat(auditColumnMigration).isEqualTo(1);
  }

  @Test
  void persistsAndLoadsOrderAggregateWithAuditing() {
    Order order = order(2);
    order.setInventoryReservationId("reservation-1");

    Order saved = orderRepository.saveAndFlush(order);
    entityManager.clear();

    Order loaded = orderRepository.findByOrderNo(saved.getOrderNo()).orElseThrow();
    assertThat(loaded.getId()).isNotNull();
    assertThat(loaded.getInventoryReservationId()).isEqualTo("reservation-1");
    assertThat(loaded.getCreatedAt()).isNotNull();
    assertThat(loaded.getUpdatedAt()).isNotNull();
    assertThat(loaded.getItems())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getSkuId()).isEqualTo("SKU-001");
              assertThat(item.getLineNo()).isEqualTo(1);
              assertThat(item.getLineAmount()).isEqualByComparingTo("198.00");
            });
  }

  @Test
  void databaseRejectsNonPositiveQuantity() {
    assertThatThrownBy(() -> orderRepository.saveAndFlush(order(0)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void failedIdempotentActionRollsBackReservedKey() {
    IdempotencyService.IdempotentOperation<String> operation =
        new IdempotencyService.IdempotentOperation<>(
            "create-order", "rollback-key", Map.of("customerId", "customer-1"), String.class);

    assertThatThrownBy(
            () ->
                idempotencyService.execute(
                    operation,
                    () -> {
                      throw new IllegalStateException("business action failed");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("business action failed");

    assertThat(
            idempotencyRecordRepository.findByOperationAndIdempotencyKey(
                "create-order", "rollback-key"))
        .isEmpty();
  }

  private static Order order(int quantity) {
    OrderItem item = new OrderItem();
    item.setSkuId("SKU-001");
    item.setProductName("Mouse");
    item.setUnitPrice(new BigDecimal("99.00"));
    item.setQuantity(quantity);
    return Order.create(
        "customer-1",
        "CNY",
        "Ada",
        "13800000000",
        "Shanghai",
        "Shanghai",
        "Pudong",
        "No. 1 Road",
        List.of(item));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class JacksonTestConfiguration {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
