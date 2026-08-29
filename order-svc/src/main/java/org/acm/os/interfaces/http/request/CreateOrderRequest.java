package org.acm.os.interfaces.http.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;
import org.acm.os.application.port.in.command.CreateOrderCommand;

/**
 * HTTP request body for {@code POST /orders}.
 *
 * <p>Mirrors {@link CreateOrderCommand} but lives in the adapter
 * layer — the {@code OrderRequestMapper} translates between the two so neither layer knows the
 * other's DTOs. No price fields are accepted; pricing is server-authoritative (design §6.2).
 */
@Data
public class CreateOrderRequest {
  @NotBlank private String customerId;
  @NotBlank private String currency;

  @Valid
  @NotNull
  private Recipient recipient;

  @Valid
  @NotEmpty
  private List<OrderLine> items;

  @Data
  public static class Recipient {
    @NotBlank private String name;
    @NotBlank private String phone;
    @NotBlank private String province;
    @NotBlank private String city;
    @NotBlank private String district;
    @NotBlank private String detailAddress;
  }

  @Data
  public static class OrderLine {
    @NotBlank private String skuId;
    @NotNull @Min(1)
    private Integer quantity;
  }
}
