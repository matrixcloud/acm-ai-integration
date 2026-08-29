package org.acm.os.application.port.in.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.acm.os.domain.order.OrderStatus;

/**
 * Application-layer input for the search-order query.
 *
 * <p>{@code status}, {@code sortBy} and {@code direction} are optional; unknown values are
 * rejected by the application service (sort whitelist) or the adapter (status parsing).
 */
@Data
public class SearchOrderQuery {
  @NotBlank private String customerId;

  private OrderStatus status;

  @NotNull
  @Min(1)
  private Integer page;

  @NotNull
  @Min(1)
  @Max(100)
  private Integer size;

  private String sortBy;
  private String direction;
}
