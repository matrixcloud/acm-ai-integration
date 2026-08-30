package org.acm.os.application.port.out;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Port for the external product catalog (design §5.4).
 *
 * <p>Implemented by a Mock adapter selected via the {@code order.adapters.product} property.
 */
public interface ProductCatalogClient {
  /**
   * Fetches saleable product snapshots for the given SKUs.
   *
   * @param skuIds SKUs to resolve
   * @return snapshots containing name, unit price, and currency — the sole source of pricing
   *     authority (design §6.2: "所有金额都来自商品价格快照并由服务端计算")
   * @throws ProductNotFoundException if any SKU is unknown or not saleable
   */
  java.util.List<ProductSnapshot> getSaleableProducts(Set<String> skuIds);

  /** Immutable product snapshot carrying the server-authoritative price. */
  record ProductSnapshot(String skuId, String productName, BigDecimal unitPrice, String currency) {}
}
