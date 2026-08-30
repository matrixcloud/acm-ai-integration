package org.acm.ca.infra.llm;

import java.util.stream.Collectors;
import org.acm.ca.application.port.out.OrderQueryClient;
import org.acm.ca.application.port.out.OrderQueryClient.OrderDetail;
import org.acm.ca.application.port.out.OrderQueryClient.OrderSummary;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Order-query tool exposed to the agent. Lets the LLM resolve a specific order by order number or
 * by recipient phone number, then ground its reply on the returned summary/detail. Tool failures
 * are converted to an error message for the LLM rather than propagated as exceptions, matching
 * {@link KbSearchTool}.
 */
@Component
public class OrderQueryTool {

  private final OrderQueryClient orderQueryClient;

  public OrderQueryTool(OrderQueryClient orderQueryClient) {
    this.orderQueryClient = orderQueryClient;
  }

  @Tool(description = "按订单号查询单个订单详情，返回订单号、状态、金额、商品与物流信息。当客户提供了具体订单号时使用此工具。")
  public String queryOrderByOrderNo(@ToolParam(description = "客户提供的订单号") String orderNo) {
    try {
      return orderQueryClient
          .findByOrderNo(orderNo)
          .map(OrderQueryTool::formatDetail)
          .orElse("未查询到订单号 %s 对应的订单。".formatted(orderNo));
    } catch (Exception e) {
      return "查询订单失败：%s。请告知客户订单服务暂不可用，建议稍后重试。".formatted(e.getMessage());
    }
  }

  @Tool(description = "按收货人手机号查询该手机号下的订单列表，返回每笔订单的订单号、状态与金额。当客户提供手机号而非订单号时使用此工具。")
  public String queryOrdersByPhone(@ToolParam(description = "客户提供的收货人手机号") String phone) {
    try {
      var orders = orderQueryClient.findByRecipientPhone(phone);
      if (orders.isEmpty()) {
        return "未查询到手机号 %s 下的订单。".formatted(phone);
      }
      return orders.stream().map(OrderQueryTool::formatSummary).collect(Collectors.joining("\n"));
    } catch (Exception e) {
      return "查询订单失败：%s。请告知客户订单服务暂不可用，建议稍后重试。".formatted(e.getMessage());
    }
  }

  private static String formatSummary(OrderSummary order) {
    return "- 订单号: %s | 状态: %s | 金额: %s %s"
        .formatted(order.orderNo(), order.status(), order.payableTotal(), order.currency());
  }

  private static String formatDetail(OrderDetail order) {
    StringBuilder sb = new StringBuilder();
    sb.append("订单号: ").append(order.orderNo()).append('\n');
    sb.append("状态: ").append(order.status()).append('\n');
    sb.append("应付金额: ")
        .append(order.payableTotal())
        .append(' ')
        .append(order.currency())
        .append('\n');
    if (order.items() != null && !order.items().isEmpty()) {
      sb.append("商品: ")
          .append(
              order.items().stream()
                  .map(item -> "%s x%d".formatted(item.productName(), item.quantity()))
                  .collect(Collectors.joining(", ")))
          .append('\n');
    }
    if (order.shipments() != null && !order.shipments().isEmpty()) {
      sb.append("物流: ");
      for (var shipment : order.shipments()) {
        sb.append("[承运 ")
            .append(shipment.carrierCode())
            .append(" | 状态 ")
            .append(shipment.status())
            .append(" | 运单号 ")
            .append(shipment.shipmentNo())
            .append("] ");
      }
      sb.append('\n');
    }
    return sb.toString();
  }
}
