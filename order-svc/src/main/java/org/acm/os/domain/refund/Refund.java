package org.acm.os.domain.refund;

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
import org.acm.os.domain.order.OrderStateConflictException;
import org.acm.os.domain.shared.AuditMetadata;
import org.acm.os.domain.shared.BusinessNumberGenerator;

@Entity
@Table(name = "refunds")
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class Refund extends AuditMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String refundNo;

  @Enumerated(EnumType.STRING)
  private RefundType type;

  @Enumerated(EnumType.STRING)
  private RefundStatus status;

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

  public static Refund autoCancel(
      String refundNo, String reason, String currency, BigDecimal amount) {
    return create(
        refundNo, RefundType.AUTO_CANCEL, RefundStatus.PROCESSING, reason, currency, amount);
  }

  public static Refund reviewed(String reason, String currency, BigDecimal amount) {
    return create(
        BusinessNumberGenerator.generate("REF"),
        RefundType.REVIEWED_REFUND,
        RefundStatus.PENDING_REVIEW,
        reason,
        currency,
        amount);
  }

  private static Refund create(
      String refundNo,
      RefundType type,
      RefundStatus status,
      String reason,
      String currency,
      BigDecimal amount) {
    Refund refund = new Refund();
    refund.refundNo = refundNo;
    refund.type = type;
    refund.status = status;
    refund.reason = reason;
    refund.currency = currency;
    refund.amount = amount;
    return refund;
  }

  public void approve(String reviewer, String comment) {
    requireStatus(RefundStatus.PENDING_REVIEW);
    this.reviewer = reviewer;
    this.reviewComment = comment;
    this.reviewedAt = LocalDateTime.now();
    this.status = RefundStatus.PROCESSING;
  }

  public void reject(String reviewer, String comment) {
    requireStatus(RefundStatus.PENDING_REVIEW);
    this.reviewer = reviewer;
    this.reviewComment = comment;
    this.reviewedAt = LocalDateTime.now();
    this.status = RefundStatus.REJECTED;
  }

  public void retry() {
    requireStatus(RefundStatus.FAILED);
    status = RefundStatus.PROCESSING;
  }

  public void markPaymentRefunded(String externalRefundNo) {
    if (status != RefundStatus.PROCESSING) {
      throw new OrderStateConflictException("Refund is not processing");
    }
    this.externalRefundNo = externalRefundNo;
    this.paymentRefunded = true;
  }

  public void markInventoryRestored() {
    if (status != RefundStatus.PROCESSING) {
      throw new OrderStateConflictException("Refund is not processing");
    }
    this.inventoryRestored = true;
  }

  public void complete() {
    if (!paymentRefunded || !inventoryRestored) {
      throw new OrderStateConflictException("Refund external steps are incomplete");
    }
    status = RefundStatus.SUCCEEDED;
    refundedAt = LocalDateTime.now();
  }

  public void fail() {
    if (status != RefundStatus.PROCESSING) {
      throw new OrderStateConflictException("Refund is not processing");
    }
    status = RefundStatus.FAILED;
  }

  private void requireStatus(RefundStatus expected) {
    if (status != expected) {
      throw new OrderStateConflictException(
          "Refund status %s does not allow this operation".formatted(status));
    }
  }
}
