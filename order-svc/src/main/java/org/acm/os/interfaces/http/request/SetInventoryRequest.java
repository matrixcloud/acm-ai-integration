package org.acm.os.interfaces.http.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetInventoryRequest {
  @NotNull @Min(0) private Integer quantity;
}
