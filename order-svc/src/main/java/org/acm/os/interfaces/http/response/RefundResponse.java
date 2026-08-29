package org.acm.os.interfaces.http.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RefundResponse {
  private String refundNo;
  private String type;
  private String status;
  private String reason;
  private String reviewComment;
  private String reviewer;
  private String externalRefundNo;
  private String currency;
  private BigDecimal amount;
  private boolean paymentRefunded;
  private boolean inventoryRestored;
  private LocalDateTime reviewedAt;
  private LocalDateTime refundedAt;
}
