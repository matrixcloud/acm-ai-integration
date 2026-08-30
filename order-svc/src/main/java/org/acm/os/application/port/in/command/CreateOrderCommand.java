package org.acm.os.application.port.in.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * Application-layer input for the create-order use case.
 *
 * <p>Carries all data needed to construct a {@link org.acm.os.domain.order.Order}; pricing
 * authority is the server (design §6.2: "所有金额都来自商品价格快照并由服务端计算。请求方不得提交 单价、订单总额或退款金额"), so no price
 * fields are accepted.
 */
@Data
public class CreateOrderCommand {
  @NotBlank private String customerId;
  @NotBlank private String currency;

  @NotBlank private String recipientName;
  @NotBlank private String recipientPhone;
  @NotBlank private String province;
  @NotBlank private String city;
  @NotBlank private String district;
  @NotBlank private String detailAddress;

  @NotEmpty private List<OrderLine> items;

  @Data
  public static class OrderLine {
    @NotBlank private String skuId;

    @NotNull
    @Min(1)
    private Integer quantity;
  }
}
