package org.acm.os.interfaces.http.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PaymentResponse {
  private String paymentNo;
  private String externalPaymentNo;
  private String status;
  private String currency;
  private BigDecimal amount;
  private String paymentToken;
  private LocalDateTime paidAt;
}
