package org.acm.os.interfaces.http.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class SetProductRequest {
  @NotBlank private String productName;
  @NotNull @DecimalMin("0.00") private BigDecimal unitPrice;
  @NotBlank private String currency;
  @NotNull private Boolean saleable;
}
