package org.acm.os.domain.order;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.acm.os.domain.shared.AuditMetadata;

/**
 * A single line within an {@link Order}.
 *
 * <p>Mutable line fields (lineNo, lineAmount) are set by the aggregate root when the item is
 * added; callers must not mutate them directly.
 */
@Entity
@Table(
    name = "order_items",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_order_items_order_line", columnNames = {"order_id", "line_no"}),
      @UniqueConstraint(name = "uk_order_items_order_sku", columnNames = {"order_id", "sku_id"}),
    })
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class OrderItem extends AuditMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Integer lineNo;
  private String skuId;
  private String productName;
  private BigDecimal unitPrice;
  private Integer quantity;
  private BigDecimal lineAmount;
}
