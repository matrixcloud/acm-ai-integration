package org.acm.os.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.acm.os.application.port.in.command.CreateOrderCommand;
import org.acm.os.application.port.in.query.SearchOrderQuery;
import org.acm.os.application.port.out.InsufficientInventoryException;
import org.acm.os.application.port.out.InventoryClient;
import org.acm.os.application.port.out.ProductCatalogClient;
import org.acm.os.application.port.out.ProductCatalogClient.ProductSnapshot;
import org.acm.os.domain.order.CurrencyMismatchException;
import org.acm.os.domain.order.DuplicateSkuException;
import org.acm.os.domain.order.Order;
import org.acm.os.domain.order.OrderRepository;
import org.acm.os.domain.order.OrderStatus;
import org.acm.os.domain.shared.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private ProductCatalogClient productCatalogClient;
  @Mock private InventoryClient inventoryClient;
  @Mock private IdempotencyService idempotencyService;
  @Captor private ArgumentCaptor<Order> orderCaptor;
  @Captor private ArgumentCaptor<PageRequest> pageRequestCaptor;

  private OrderService service;

  @BeforeEach
  void setUp() {
    service =
        new OrderService(
            orderRepository, productCatalogClient, inventoryClient, idempotencyService);
    lenient()
        .when(idempotencyService.execute(any(), any()))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
  }

  @Test
  void createEnrichesReservesAndPersistsOrder() {
    CreateOrderCommand command = command(line("SKU-001", 2), line("SKU-002", 1));
    when(productCatalogClient.getSaleableProducts(Set.of("SKU-001", "SKU-002")))
        .thenReturn(
            List.of(
                snapshot("SKU-001", "Mouse", "99.00", "CNY"),
                snapshot("SKU-002", "Keyboard", "399.00", "CNY")));
    when(inventoryClient.reserve(any(), anyList(), any()))
        .thenReturn(new InventoryClient.InventoryReservation("reservation-1"));
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Order created = service.create(command, "key-1");

    verify(orderRepository).saveAndFlush(orderCaptor.capture());
    assertThat(created).isSameAs(orderCaptor.getValue());
    assertThat(created.getInventoryReservationId()).isEqualTo("reservation-1");
    assertThat(created.getPayableTotal()).isEqualByComparingTo("597.00");
    assertThat(created.getItems())
        .extracting(item -> item.getSkuId(), item -> item.getQuantity())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("SKU-001", 2),
            org.assertj.core.groups.Tuple.tuple("SKU-002", 1));
    verify(inventoryClient)
        .reserve(
            created.getOrderNo(),
            List.of(
                new InventoryClient.InventoryItem("SKU-001", 2),
                new InventoryClient.InventoryItem("SKU-002", 1)),
            "key-1");
  }

  @Test
  void createPassesExplicitIdempotentOperation() {
    CreateOrderCommand command = command(line("SKU-001", 1));
    when(productCatalogClient.getSaleableProducts(anySet()))
        .thenReturn(List.of(snapshot("SKU-001", "Mouse", "99.00", "CNY")));
    when(inventoryClient.reserve(any(), anyList(), any()))
        .thenReturn(new InventoryClient.InventoryReservation("reservation-1"));
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<IdempotencyService.IdempotentOperation<Order>> operationCaptor =
        ArgumentCaptor.forClass(IdempotencyService.IdempotentOperation.class);

    service.create(command, "key-1");

    verify(idempotencyService).execute(operationCaptor.capture(), any());
    IdempotencyService.IdempotentOperation<Order> operation = operationCaptor.getValue();
    assertThat(operation.operation()).isEqualTo(OrderService.CREATE_OPERATION);
    assertThat(operation.idempotencyKey()).isEqualTo("key-1");
    assertThat(operation.request()).isSameAs(command);
    assertThat(operation.responseType()).isEqualTo(Order.class);
  }

  @Test
  void createRejectsDuplicateSkuBeforeExternalCalls() {
    CreateOrderCommand command = command(line("SKU-001", 1), line("SKU-001", 2));

    assertThatThrownBy(() -> service.create(command, "key-1"))
        .isInstanceOf(DuplicateSkuException.class);

    verifyNoInteractions(productCatalogClient, inventoryClient, orderRepository);
  }

  @Test
  void createRejectsProductCurrencyMismatchBeforeInventoryReservation() {
    CreateOrderCommand command = command(line("SKU-001", 1));
    when(productCatalogClient.getSaleableProducts(Set.of("SKU-001")))
        .thenReturn(List.of(snapshot("SKU-001", "Mouse", "99.00", "USD")));

    assertThatThrownBy(() -> service.create(command, "key-1"))
        .isInstanceOf(CurrencyMismatchException.class)
        .hasMessageContaining("order CNY but product USD");

    verifyNoInteractions(inventoryClient, orderRepository);
  }

  @Test
  void createFailsWhenCatalogOmitsRequestedSnapshot() {
    CreateOrderCommand command = command(line("SKU-001", 1));
    when(productCatalogClient.getSaleableProducts(Set.of("SKU-001"))).thenReturn(List.of());

    assertThatThrownBy(() -> service.create(command, "key-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No snapshot for SKU 'SKU-001'");

    verifyNoInteractions(inventoryClient, orderRepository);
  }

  @Test
  void createDoesNotPersistWhenInventoryReservationFails() {
    CreateOrderCommand command = command(line("SKU-001", 101));
    when(productCatalogClient.getSaleableProducts(Set.of("SKU-001")))
        .thenReturn(List.of(snapshot("SKU-001", "Mouse", "99.00", "CNY")));
    when(inventoryClient.reserve(any(), anyList(), any()))
        .thenThrow(new InsufficientInventoryException("insufficient"));

    assertThatThrownBy(() -> service.create(command, "key-1"))
        .isInstanceOf(InsufficientInventoryException.class);

    verify(orderRepository, never()).saveAndFlush(any());
  }

  @Test
  void createReleasesReservationWhenPersistenceFails() {
    RuntimeException persistenceFailure = new RuntimeException("database failed");
    CreateOrderCommand command = command(line("SKU-001", 1));
    when(productCatalogClient.getSaleableProducts(Set.of("SKU-001")))
        .thenReturn(List.of(snapshot("SKU-001", "Mouse", "99.00", "CNY")));
    when(inventoryClient.reserve(any(), anyList(), any()))
        .thenReturn(new InventoryClient.InventoryReservation("reservation-1"));
    when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(persistenceFailure);

    assertThatThrownBy(() -> service.create(command, "key-1")).isSameAs(persistenceFailure);

    verify(inventoryClient).release("reservation-1", "key-1");
  }

  @Test
  void createPreservesPersistenceFailureWhenCompensationAlsoFails() {
    RuntimeException persistenceFailure = new RuntimeException("database failed");
    RuntimeException releaseFailure = new RuntimeException("release failed");
    CreateOrderCommand command = command(line("SKU-001", 1));
    when(productCatalogClient.getSaleableProducts(Set.of("SKU-001")))
        .thenReturn(List.of(snapshot("SKU-001", "Mouse", "99.00", "CNY")));
    when(inventoryClient.reserve(any(), anyList(), any()))
        .thenReturn(new InventoryClient.InventoryReservation("reservation-1"));
    when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(persistenceFailure);
    doThrow(releaseFailure).when(inventoryClient).release("reservation-1", "key-1");

    assertThatThrownBy(() -> service.create(command, "key-1"))
        .isSameAs(persistenceFailure)
        .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(releaseFailure));

    verify(inventoryClient).release("reservation-1", "key-1");
  }

  @Test
  void searchUsesDefaultSortAndCustomerRepositoryMethod() {
    SearchOrderQuery query = query(null, null, null);
    Page<Order> expected = new PageImpl<>(List.of());
    when(orderRepository.findByCustomerId(eq("customer-1"), any())).thenReturn(expected);

    Page<Order> result = service.search(query);

    assertThat(result).isSameAs(expected);
    verify(orderRepository).findByCustomerId(eq("customer-1"), pageRequestCaptor.capture());
    PageRequest request = pageRequestCaptor.getValue();
    assertThat(request.getPageNumber()).isZero();
    assertThat(request.getPageSize()).isEqualTo(20);
    assertThat(request.getSort().getOrderFor("createdAt").getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void searchUsesStatusAndExplicitSort() {
    SearchOrderQuery query = query(OrderStatus.PENDING_PAYMENT, "orderNo", "asc");
    Page<Order> expected = new PageImpl<>(List.of());
    when(orderRepository.findByCustomerIdAndStatus(
            eq("customer-1"), eq(OrderStatus.PENDING_PAYMENT), any()))
        .thenReturn(expected);

    assertThat(service.search(query)).isSameAs(expected);

    verify(orderRepository)
        .findByCustomerIdAndStatus(
            eq("customer-1"), eq(OrderStatus.PENDING_PAYMENT), pageRequestCaptor.capture());
    assertThat(pageRequestCaptor.getValue().getSort().getOrderFor("orderNo").getDirection())
        .isEqualTo(Sort.Direction.ASC);
  }

  @Test
  void searchRejectsUnsupportedSortFieldAndDirection() {
    assertThatThrownBy(() -> service.search(query(null, "id", "asc")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unsupported sort field 'id'");
    assertThatThrownBy(() -> service.search(query(null, "status", "sideways")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unsupported sort direction 'sideways'");

    verifyNoInteractions(orderRepository);
  }

  @Test
  void searchByRecipientPhoneUsesPhoneRepositoryMethod() {
    SearchOrderQuery query = phoneQuery(null, null, null);
    Page<Order> expected = new PageImpl<>(List.of());
    when(orderRepository.findByRecipientPhone(eq("13800000002"), any())).thenReturn(expected);

    Page<Order> result = service.search(query);

    assertThat(result).isSameAs(expected);
    verify(orderRepository).findByRecipientPhone(eq("13800000002"), pageRequestCaptor.capture());
    assertThat(pageRequestCaptor.getValue().getSort().getOrderFor("createdAt").getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void searchRejectsWhenNeitherOrBothKeysProvided() {
    SearchOrderQuery neither = new SearchOrderQuery();
    neither.setPage(1);
    neither.setSize(20);

    assertThatThrownBy(() -> service.search(neither))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Exactly one of customerId or recipientPhone must be provided");

    SearchOrderQuery both = new SearchOrderQuery();
    both.setCustomerId("customer-1");
    both.setRecipientPhone("13800000002");
    both.setPage(1);
    both.setSize(20);

    assertThatThrownBy(() -> service.search(both))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Exactly one of customerId or recipientPhone must be provided");

    verifyNoInteractions(orderRepository);
  }

  private static CreateOrderCommand command(CreateOrderCommand.OrderLine... lines) {
    CreateOrderCommand command = new CreateOrderCommand();
    command.setCustomerId("customer-1");
    command.setCurrency("CNY");
    command.setRecipientName("Ada");
    command.setRecipientPhone("13800000000");
    command.setProvince("Shanghai");
    command.setCity("Shanghai");
    command.setDistrict("Pudong");
    command.setDetailAddress("No. 1 Road");
    command.setItems(List.of(lines));
    return command;
  }

  private static CreateOrderCommand.OrderLine line(String skuId, int quantity) {
    CreateOrderCommand.OrderLine line = new CreateOrderCommand.OrderLine();
    line.setSkuId(skuId);
    line.setQuantity(quantity);
    return line;
  }

  private static ProductSnapshot snapshot(
      String skuId, String name, String price, String currency) {
    return new ProductSnapshot(skuId, name, new BigDecimal(price), currency);
  }

  private static SearchOrderQuery query(OrderStatus status, String sortBy, String direction) {
    SearchOrderQuery query = new SearchOrderQuery();
    query.setCustomerId("customer-1");
    query.setStatus(status);
    query.setPage(1);
    query.setSize(20);
    query.setSortBy(sortBy);
    query.setDirection(direction);
    return query;
  }

  private static SearchOrderQuery phoneQuery(OrderStatus status, String sortBy, String direction) {
    SearchOrderQuery query = new SearchOrderQuery();
    query.setRecipientPhone("13800000002");
    query.setStatus(status);
    query.setPage(1);
    query.setSize(20);
    query.setSortBy(sortBy);
    query.setDirection(direction);
    return query;
  }
}
