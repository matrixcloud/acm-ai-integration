package org.acm.os.interfaces.http.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class CreateShipmentRequest {
  @NotBlank private String carrierCode;
  @Valid @NotEmpty private List<Item> items;

  @Data
  public static class Item {
    @NotNull private Long orderItemId;
    @NotNull @Min(1) private Integer quantity;
  }
}
