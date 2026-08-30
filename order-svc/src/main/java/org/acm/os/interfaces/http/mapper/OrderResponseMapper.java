package org.acm.os.interfaces.http.mapper;

import java.util.List;
import org.acm.os.domain.order.Order;
import org.acm.os.domain.payment.Payment;
import org.acm.os.domain.refund.Refund;
import org.acm.os.domain.shipment.Shipment;
import org.acm.os.domain.shipment.ShipmentItem;
import org.acm.os.interfaces.http.response.CreateOrderResponse;
import org.acm.os.interfaces.http.response.OrderSummaryResponse;
import org.acm.os.interfaces.http.response.PaymentResponse;
import org.acm.os.interfaces.http.response.RefundResponse;
import org.acm.os.interfaces.http.response.ShipmentResponse;
import org.mapstruct.Mapper;

/**
 * Maps domain {@link Order} instances to outbound HTTP response DTOs.
 *
 * <p>Lives in the adapter layer — the adapter owns translating domain results back into
 * transport-level projections. The domain layer never references {@code CreateOrderResponse}.
 */
@Mapper(componentModel = "spring")
public interface OrderResponseMapper {
  CreateOrderResponse toResponse(Order order);

  List<CreateOrderResponse> toResponseList(List<Order> orders);

  OrderSummaryResponse toSummaryResponse(Order order);

  List<OrderSummaryResponse> toSummaryResponseList(List<Order> orders);

  CreateOrderResponse.Item toItem(org.acm.os.domain.order.OrderItem item);

  List<CreateOrderResponse.Item> toItems(List<org.acm.os.domain.order.OrderItem> items);

  PaymentResponse toPaymentResponse(Payment payment);

  RefundResponse toRefundResponse(Refund refund);

  ShipmentResponse toShipmentResponse(Shipment shipment);

  ShipmentResponse.Item toShipmentItemResponse(ShipmentItem item);
}
