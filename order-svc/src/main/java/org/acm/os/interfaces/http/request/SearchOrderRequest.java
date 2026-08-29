package org.acm.os.interfaces.http.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.acm.os.application.port.in.query.SearchOrderQuery;

/**
 * HTTP request for {@code GET /orders/search}.
 *
 * <p>Flat query-parameter binding (design §9.1: {@code customerId=&status=&page=&size=}); the
 * {@code OrderRequestMapper} translates it into a {@link
 * SearchOrderQuery}.
 */
@Data
public class SearchOrderRequest {
  @NotBlank private String customerId;

  private String status;

  @Min(1)
  private Integer page = 1;

  @Min(1)
  @Max(100)
  private Integer size = 20;

  private String sortBy;
  private String direction;
}
