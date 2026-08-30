package org.acm.ca.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.acm.ca.application.port.out.OrderQueryClient;
import org.acm.ca.application.port.out.OrderQueryClient.DetailItem;
import org.acm.ca.application.port.out.OrderQueryClient.DetailShipment;
import org.acm.ca.application.port.out.OrderQueryClient.OrderDetail;
import org.acm.ca.application.port.out.OrderQueryClient.OrderSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderQueryToolTest {

  private OrderQueryClient orderQueryClient;
  private OrderQueryTool tool;

  @BeforeEach
  void setUp() {
    orderQueryClient = mock(OrderQueryClient.class);
    tool = new OrderQueryTool(orderQueryClient);
  }

  @Test
  void queryOrderByOrderNoFormatsDetail() {
    when(orderQueryClient.findByOrderNo("ORD-1")).thenReturn(Optional.of(detail()));

    String result = tool.queryOrderByOrderNo("ORD-1");

    assertThat(result)
        .contains("ORD-1")
        .contains("SHIPPED")
        .contains("SKU-002")
        .contains("MOCK_EXPRESS");
  }

  @Test
  void queryOrderByOrderNoReturnsNotFoundMessage() {
    when(orderQueryClient.findByOrderNo("ORD-404")).thenReturn(Optional.empty());

    assertThat(tool.queryOrderByOrderNo("ORD-404")).contains("未查询到订单号 ORD-404");
  }

  @Test
  void queryOrdersByPhoneFormatsSummaries() {
    when(orderQueryClient.findByRecipientPhone("13800000002"))
        .thenReturn(
            List.of(
                new OrderSummary("ORD-9", "PAID", new BigDecimal("99.00"), "CNY", null),
                new OrderSummary("ORD-10", "SHIPPED", new BigDecimal("885.00"), "CNY", null)));

    String result = tool.queryOrdersByPhone("13800000002");

    assertThat(result).contains("ORD-9").contains("PAID").contains("ORD-10").contains("SHIPPED");
  }

  @Test
  void queryOrdersByPhoneReturnsNotFoundMessage() {
    when(orderQueryClient.findByRecipientPhone("13800000000")).thenReturn(List.of());

    assertThat(tool.queryOrdersByPhone("13800000000")).contains("未查询到手机号 13800000000");
  }

  @Test
  void queryOrdersByPhoneReturnsErrorMessageOnFailure() {
    when(orderQueryClient.findByRecipientPhone("13800000000"))
        .thenThrow(new RuntimeException("连接失败"));

    assertThat(tool.queryOrdersByPhone("13800000000")).contains("查询订单失败");
  }

  private static OrderDetail detail() {
    return new OrderDetail(
        "ORD-1",
        "cust-001",
        "SHIPPED",
        "CNY",
        new BigDecimal("885.00"),
        new BigDecimal("885.00"),
        List.of(new DetailItem("SKU-002", 2, new BigDecimal("399.00"))),
        List.of(new DetailShipment("SHP-1", "MOCK_EXPRESS", "SHIPPED")));
  }
}
