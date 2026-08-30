package org.acm.os.interfaces.http.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.acm.os.application.port.in.command.CreateOrderCommand;
import org.acm.os.application.port.in.query.SearchOrderQuery;
import org.acm.os.domain.order.OrderStatus;
import org.acm.os.domain.shared.InvalidRequestException;
import org.acm.os.interfaces.http.request.CreateOrderRequest;
import org.acm.os.interfaces.http.request.SearchOrderRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class OrderRequestMapperTest {

  private final OrderRequestMapper mapper = Mappers.getMapper(OrderRequestMapper.class);

  @Test
  void mapsCreateRequestAndFlattensRecipient() {
    CreateOrderRequest request = new CreateOrderRequest();
    request.setCustomerId("customer-1");
    request.setCurrency("CNY");
    CreateOrderRequest.Recipient recipient = new CreateOrderRequest.Recipient();
    recipient.setName("Ada");
    recipient.setPhone("13800000000");
    recipient.setProvince("Shanghai");
    recipient.setCity("Shanghai");
    recipient.setDistrict("Pudong");
    recipient.setDetailAddress("No. 1 Road");
    request.setRecipient(recipient);
    CreateOrderRequest.OrderLine line = new CreateOrderRequest.OrderLine();
    line.setSkuId("SKU-001");
    line.setQuantity(2);
    request.setItems(List.of(line));

    CreateOrderCommand command = mapper.toCommand(request);

    assertThat(command.getCustomerId()).isEqualTo("customer-1");
    assertThat(command.getCurrency()).isEqualTo("CNY");
    assertThat(command.getRecipientName()).isEqualTo("Ada");
    assertThat(command.getRecipientPhone()).isEqualTo("13800000000");
    assertThat(command.getProvince()).isEqualTo("Shanghai");
    assertThat(command.getCity()).isEqualTo("Shanghai");
    assertThat(command.getDistrict()).isEqualTo("Pudong");
    assertThat(command.getDetailAddress()).isEqualTo("No. 1 Road");
    assertThat(command.getItems())
        .singleElement()
        .satisfies(
            mapped -> {
              assertThat(mapped.getSkuId()).isEqualTo("SKU-001");
              assertThat(mapped.getQuantity()).isEqualTo(2);
            });
  }

  @Test
  void mapsSearchRequestAndParsesStatus() {
    SearchOrderRequest request = new SearchOrderRequest();
    request.setCustomerId("customer-1");
    request.setStatus("PENDING_PAYMENT");
    request.setPage(2);
    request.setSize(10);
    request.setSortBy("orderNo");
    request.setDirection("asc");

    SearchOrderQuery query = mapper.toQuery(request);

    assertThat(query.getCustomerId()).isEqualTo("customer-1");
    assertThat(query.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    assertThat(query.getPage()).isEqualTo(2);
    assertThat(query.getSize()).isEqualTo(10);
    assertThat(query.getSortBy()).isEqualTo("orderNo");
    assertThat(query.getDirection()).isEqualTo("asc");
  }

  @Test
  void mapsSearchRequestByRecipientPhone() {
    SearchOrderRequest request = new SearchOrderRequest();
    request.setRecipientPhone("13800000002");
    request.setPage(1);
    request.setSize(20);

    SearchOrderQuery query = mapper.toQuery(request);

    assertThat(query.getRecipientPhone()).isEqualTo("13800000002");
    assertThat(query.getCustomerId()).isNull();
  }

  @Test
  void parseStatusTreatsMissingValuesAsNoFilter() {
    assertThat(mapper.parseStatus(null)).isNull();
    assertThat(mapper.parseStatus(" ")).isNull();
  }

  @Test
  void parseStatusRejectsUnsupportedValue() {
    assertThatThrownBy(() -> mapper.parseStatus("UNKNOWN"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unsupported order status 'UNKNOWN'");
  }
}
