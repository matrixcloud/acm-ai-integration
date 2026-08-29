package org.acm.os.domain.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.acm.os.domain.order.OrderStateConflictException;
import org.junit.jupiter.api.Test;

class RefundTest {

  @Test
  void reviewTransitionsAreGuarded() {
    Refund refund = Refund.reviewed("reason", "CNY", new BigDecimal("99.00"));
    refund.approve("admin", "ok");
    assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSING);
    assertThat(refund.getReviewedAt()).isNotNull();
    assertThatThrownBy(() -> refund.approve("admin", "again"))
        .isInstanceOf(OrderStateConflictException.class);
    assertThatThrownBy(refund::complete).isInstanceOf(OrderStateConflictException.class);
    refund.markPaymentRefunded("external");
    refund.markInventoryRestored();
    refund.complete();
    assertThatThrownBy(refund::fail).isInstanceOf(OrderStateConflictException.class);
  }

  @Test
  void rejectionAndRetryRulesAreGuarded() {
    Refund rejected = Refund.reviewed("reason", "CNY", BigDecimal.ONE);
    rejected.reject("admin", "no");
    assertThat(rejected.getStatus()).isEqualTo(RefundStatus.REJECTED);
    assertThatThrownBy(rejected::retry).isInstanceOf(OrderStateConflictException.class);

    Refund failed = Refund.autoCancel("REF-1", "reason", "CNY", BigDecimal.ONE);
    failed.fail();
    failed.retry();
    assertThat(failed.getStatus()).isEqualTo(RefundStatus.PROCESSING);
  }
}
