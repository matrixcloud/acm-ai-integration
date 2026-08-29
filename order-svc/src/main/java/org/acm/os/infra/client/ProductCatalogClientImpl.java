package org.acm.os.infra.client;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.acm.os.application.port.out.ProductCatalogClient;
import org.acm.os.application.port.out.ProductNotFoundException;
import org.springframework.stereotype.Component;

/**
 * In-memory Mock implementation of {@link ProductCatalogClient} (design §11.1: "Mock 商品目录保存
 * SKU、名称、单价、币种和可售状态").
 *
 * <p>Always registered — there is no profile gate. The catalog is a fixed in-memory map, so this
 * adapter is not suitable for production: replace it with a real client before any non-demo
 * deployment. Unknown SKUs throw explicitly — no silent fallback.
 */
@Component
public class ProductCatalogClientImpl implements ProductCatalogClient {
  static final Map<String, ProductSnapshot> CATALOG =
      Map.of(
          "SKU-001",
              new ProductSnapshot("SKU-001", "Wireless Mouse", new BigDecimal("99.00"), "CNY"),
          "SKU-002",
              new ProductSnapshot("SKU-002", "Mechanical Keyboard", new BigDecimal("399.00"), "CNY"),
          "SKU-003",
              new ProductSnapshot("SKU-003", "USB-C Cable 2m", new BigDecimal("29.00"), "CNY"));

  @Override
  public List<ProductSnapshot> getSaleableProducts(Set<String> skuIds) {
    List<ProductSnapshot> found = new ArrayList<>(skuIds.size());
    for (String skuId : skuIds) {
      ProductSnapshot snapshot = CATALOG.get(skuId);
      if (snapshot == null) {
        throw new ProductNotFoundException("SKU '%s' is not available".formatted(skuId));
      }
      found.add(snapshot);
    }
    return found;
  }
}
