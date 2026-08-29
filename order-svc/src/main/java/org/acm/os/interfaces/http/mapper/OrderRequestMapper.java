package org.acm.os.interfaces.http.mapper;

import org.acm.os.interfaces.http.request.CreateOrderRequest;
import org.acm.os.interfaces.http.request.SearchOrderRequest;
import org.acm.os.application.port.in.command.CreateOrderCommand;
import org.acm.os.application.port.in.query.SearchOrderQuery;
import org.acm.os.domain.order.OrderStatus;
import org.acm.os.domain.shared.InvalidRequestException;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps inbound HTTP request DTOs to application-layer commands/queries.
 *
 * <p>Lives in the adapter layer — the adapter owns translating its own DTOs into the application
 * layer's vocabulary. The application layer never references {@code CreateOrderRequest}.
 */
@Mapper(componentModel = "spring")
public interface OrderRequestMapper {
  /**
   * Maps a create-order request to its command, flattening the nested {@code Recipient} into the
   * top-level address fields expected by {@link CreateOrderCommand}.
   */
  @Mapping(source = "recipient.name", target = "recipientName")
  @Mapping(source = "recipient.phone", target = "recipientPhone")
  @Mapping(source = "recipient.province", target = "province")
  @Mapping(source = "recipient.city", target = "city")
  @Mapping(source = "recipient.district", target = "district")
  @Mapping(source = "recipient.detailAddress", target = "detailAddress")
  CreateOrderCommand toCommand(CreateOrderRequest request);

  /** Maps a flat search request to its query, parsing the optional status filter. */
  default SearchOrderQuery toQuery(SearchOrderRequest request) {
    SearchOrderQuery query = new SearchOrderQuery();
    query.setCustomerId(request.getCustomerId());
    query.setStatus(parseStatus(request.getStatus()));
    query.setPage(request.getPage());
    query.setSize(request.getSize());
    query.setSortBy(request.getSortBy());
    query.setDirection(request.getDirection());
    return query;
  }

  default OrderStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return OrderStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException(
          "Unsupported order status '%s'".formatted(status));
    }
  }
}
