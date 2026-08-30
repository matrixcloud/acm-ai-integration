package org.acm.os.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.acm.os.application.exception.PersistedRetryableFailureException;
import org.acm.os.application.exception.RetryableOperationException;
import org.acm.os.application.idempotency.IdempotencyRecordRepository;
import org.acm.os.domain.order.Order;
import org.acm.os.domain.order.OrderItem;
import org.acm.os.domain.order.OrderRepository;
import org.acm.os.domain.order.OrderStatus;
import org.acm.os.domain.payment.Payment;
import org.acm.os.domain.refund.Refund;
import org.acm.os.domain.shipment.Shipment;
import org.acm.os.domain.shipment.ShipmentItem;
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
import org.springframework.data.domain.PageRequest;
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
    jdbcTemplate.execute(
        "TRUNCATE TABLE shipment_items, shipments, refunds, payments, order_items, orders, idempotency_records");
  }

  @Test
  void flywayCreatesExpectedSchema() {
    Integer auditColumnMigration =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '3' and success",
            Integer.class);

    assertThat(auditColumnMigration).isEqualTo(1);
  }

  @Test
  void persistsPaymentRefundAndShipmentLifecycle() {
    Order shippedOrder = order(2);
    orderRepository.saveAndFlush(shippedOrder);
    Payment shipmentPayment = shippedOrder.addPayment("payment-token-1");
    shippedOrder.markPaid(shipmentPayment, "external-payment-1");
    Shipment shipment =
        Shipment.create(
            "SHP-INTEGRATION-1",
            "MOCK_EXPRESS",
            "TRACK-INTEGRATION-1",
            List.of(ShipmentItem.of(shippedOrder.getItems().get(0).getId(), 2)));
    shippedOrder.allocateShipment(shipment);
    shippedOrder.confirmShipmentDelivered(shipment.getShipmentNo());
    orderRepository.saveAndFlush(shippedOrder);

    Order refundedOrder = order(1);
    orderRepository.saveAndFlush(refundedOrder);
    Payment refundPayment = refundedOrder.addPayment("payment-token-2");
    refundedOrder.markPaid(refundPayment, "external-payment-2");
    Refund refund = refundedOrder.requestRefund("integration refund");
    refundedOrder.approveRefund(refund, "admin", "approved");
    refund.markPaymentRefunded("external-refund-1");
    refund.markInventoryRestored();
    refundedOrder.completeRefund(refund);
    orderRepository.saveAndFlush(refundedOrder);

    assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Integer.class))
        .isEqualTo(2);
    assertThat(jdbcTemplate.queryForObject("select count(*) from refunds", Integer.class))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("select count(*) from shipments", Integer.class))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("select count(*) from shipment_items", Integer.class))
        .isEqualTo(1);
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

  @Test
  void retryableFailureCommitsFailureStateAndReleasesKey() {
    Order saved = orderRepository.saveAndFlush(order(1));
    IdempotencyService.IdempotentOperation<Order> operation =
        new IdempotencyService.IdempotentOperation<>(
            "cancel-order", "retryable-key", Map.of("orderNo", saved.getOrderNo()), Order.class);

    assertThatThrownBy(
            () ->
                idempotencyService.executeRetryable(
                    operation,
                    () -> {
                      Order current =
                          orderRepository.findByOrderNo(saved.getOrderNo()).orElseThrow();
                      current.cancelPending();
                      orderRepository.saveAndFlush(current);
                      throw new PersistedRetryableFailureException(
                          new IllegalStateException("external failed"));
                    }))
        .isInstanceOf(RetryableOperationException.class)
        .hasRootCauseMessage("external failed");

    entityManager.clear();
    assertThat(orderRepository.findByOrderNo(saved.getOrderNo()).orElseThrow().getStatus())
        .isEqualTo(org.acm.os.domain.order.OrderStatus.CANCELED);
    assertThat(
            idempotencyRecordRepository.findByOperationAndIdempotencyKey(
                "cancel-order", "retryable-key"))
        .isEmpty();
  }

  @Test
  void completedOrderOperationCanReplaySerializedAggregate() {
    Order saved = orderRepository.saveAndFlush(order(1));
    IdempotencyService.IdempotentOperation<Order> operation =
        new IdempotencyService.IdempotentOperation<>(
            "order-replay", "replay-key", Map.of("orderNo", saved.getOrderNo()), Order.class);

    Order first = idempotencyService.execute(operation, () -> saved);
    Order replayed =
        idempotencyService.execute(
            operation,
            () -> {
              throw new AssertionError("completed operation must not execute again");
            });

    assertThat(replayed.getOrderNo()).isEqualTo(first.getOrderNo());
    assertThat(replayed.getItems()).hasSize(1);
  }

  @Test
  void externalPaymentNumberCanBeClaimedOnlyOnce() {
    Order first = orderRepository.saveAndFlush(order(1));
    Payment firstPayment = first.addPayment("token-claim-1");
    first.claimPaymentNotification(firstPayment, "external-claim-1");
    orderRepository.saveAndFlush(first);

    Order second = orderRepository.saveAndFlush(order(1));
    Payment secondPayment = second.addPayment("token-claim-2");
    second.claimPaymentNotification(secondPayment, "external-claim-1");

    assertThatThrownBy(() -> orderRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
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

  @Test
  void findsOrdersByRecipientPhone() {
    Order saved = orderRepository.saveAndFlush(order(2));

    var page = orderRepository.findByRecipientPhone("13800000000", PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().get(0).getOrderNo()).isEqualTo(saved.getOrderNo());
  }

  @Test
  void findsOrdersByRecipientPhoneAndStatus() {
    orderRepository.saveAndFlush(order(2));

    var pending =
        orderRepository.findByRecipientPhoneAndStatus(
            "13800000000", OrderStatus.PENDING_PAYMENT, PageRequest.of(0, 10));
    var paid =
        orderRepository.findByRecipientPhoneAndStatus(
            "13800000000", OrderStatus.PAID, PageRequest.of(0, 10));

    assertThat(pending.getTotalElements()).isEqualTo(1);
    assertThat(paid.getTotalElements()).isZero();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class JacksonTestConfiguration {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
