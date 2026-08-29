package org.acm.os.interfaces.http.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.acm.common.http.PageResponse;
import org.acm.os.application.port.in.OrderUseCase;
import org.acm.os.application.port.in.command.CreateOrderCommand;
import org.acm.os.application.port.in.query.SearchOrderQuery;
import org.acm.os.domain.order.Order;
import org.acm.os.interfaces.http.mapper.OrderRequestMapper;
import org.acm.os.interfaces.http.mapper.OrderResponseMapper;
import org.acm.os.interfaces.http.request.CreateOrderRequest;
import org.acm.os.interfaces.http.request.SearchOrderRequest;
import org.acm.os.interfaces.http.response.CreateOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

  @Mock private OrderUseCase orderUseCase;
  @Mock private OrderRequestMapper requestMapper;
  @Mock private OrderResponseMapper responseMapper;

  private OrderController controller;

  @BeforeEach
  void setUp() {
    controller = new OrderController(orderUseCase, requestMapper, responseMapper);
  }

  @Test
  void createMapsRequestAndReturnsCreatedResponse() {
    CreateOrderRequest request = new CreateOrderRequest();
    CreateOrderCommand command = new CreateOrderCommand();
    Order order = org.mockito.Mockito.mock(Order.class);
    CreateOrderResponse response = new CreateOrderResponse();
    when(requestMapper.toCommand(request)).thenReturn(command);
    when(orderUseCase.create(command, "key-1")).thenReturn(order);
    when(responseMapper.toResponse(order)).thenReturn(response);

    ResponseEntity<CreateOrderResponse> result = controller.create(request, "key-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody()).isSameAs(response);
    verify(orderUseCase).create(command, "key-1");
  }

  @Test
  void searchMapsPageAndUsesZeroBasedResponseMetadata() {
    SearchOrderRequest request = new SearchOrderRequest();
    SearchOrderQuery query = new SearchOrderQuery();
    Order order = org.mockito.Mockito.mock(Order.class);
    CreateOrderResponse response = new CreateOrderResponse();
    when(requestMapper.toQuery(request)).thenReturn(query);
    when(orderUseCase.search(query))
        .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(1, 2), 5));
    when(responseMapper.toResponseList(List.of(order))).thenReturn(List.of(response));

    PageResponse<CreateOrderResponse> result = controller.search(request);

    assertThat(result.getItems()).containsExactly(response);
    assertThat(result.getPage().getNumber()).isEqualTo(1);
    assertThat(result.getPage().getSize()).isEqualTo(2);
    assertThat(result.getPage().getTotalElements()).isEqualTo(5);
    assertThat(result.getPage().getTotalPages()).isEqualTo(3);
  }
}
