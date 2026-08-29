package org.acm.os.interfaces.http.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OrderSummaryResponse {
  private String orderNo;
  private String customerId;
  private String status;
  private String currency;
  private BigDecimal payableTotal;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
