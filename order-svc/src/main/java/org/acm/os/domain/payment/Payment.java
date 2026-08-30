package org.acm.os.domain.payment;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.acm.os.domain.shared.AuditMetadata;
import org.acm.os.domain.shared.BusinessNumberGenerator;

@Entity
@Table(name = "payments")
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class Payment extends AuditMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String paymentNo;
  private String externalPaymentNo;

  @Enumerated(EnumType.STRING)
  private PaymentStatus status;

  private String currency;
  private BigDecimal amount;
  private String paymentToken;
  private LocalDateTime paidAt;

  public static Payment create(String currency, BigDecimal amount, String paymentToken) {
    Payment payment = new Payment();
    payment.paymentNo = BusinessNumberGenerator.generate("PAY");
    payment.status = PaymentStatus.CREATED;
    payment.currency = currency;
    payment.amount = amount;
    payment.paymentToken = paymentToken;
    return payment;
  }

  public void succeed(String externalPaymentNo) {
    if (status == PaymentStatus.SUCCEEDED) {
      if (!this.externalPaymentNo.equals(externalPaymentNo)) {
        throw new IllegalStateException("Payment already succeeded with another external number");
      }
      return;
    }
    if (status != PaymentStatus.CREATED && status != PaymentStatus.FAILED) {
      throw new IllegalStateException("Payment cannot succeed from status " + status);
    }
    this.externalPaymentNo = externalPaymentNo;
    this.status = PaymentStatus.SUCCEEDED;
    this.paidAt = LocalDateTime.now();
  }

  public void claimExternalPaymentNo(String externalPaymentNo) {
    if (status == PaymentStatus.SUCCEEDED) {
      succeed(externalPaymentNo);
      return;
    }
    if (this.externalPaymentNo != null && !this.externalPaymentNo.equals(externalPaymentNo)) {
      throw new IllegalStateException("Payment notification uses another external number");
    }
    this.externalPaymentNo = externalPaymentNo;
  }

  public void fail() {
    if (status == PaymentStatus.SUCCEEDED) {
      throw new IllegalStateException("Succeeded payment cannot fail");
    }
    status = PaymentStatus.FAILED;
  }
}
