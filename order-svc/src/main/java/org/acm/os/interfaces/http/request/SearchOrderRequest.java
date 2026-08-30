package org.acm.os.interfaces.http.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.acm.os.application.port.in.query.SearchOrderQuery;

/**
 * Flat query-parameter binding (design §9.1); searches by {@code customerId} or {@code
 * recipientPhone} (exactly one required). The {@code OrderRequestMapper} translates it into a
 * {@link SearchOrderQuery}.
 */
@Data
public class SearchOrderRequest {
  private String customerId;

  private String recipientPhone;

  private String status;

  @Min(1)
  private Integer page = 1;

  @Min(1)
  @Max(100)
  private Integer size = 20;

  private String sortBy;
  private String direction;
}
