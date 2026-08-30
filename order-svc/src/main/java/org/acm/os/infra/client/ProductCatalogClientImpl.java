package org.acm.os.infra.client;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.acm.os.application.port.out.ProductCatalogClient;
import org.acm.os.application.port.out.ProductNotFoundException;
import org.acm.os.domain.shared.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In-memory Mock implementation of {@link ProductCatalogClient} (design §11.1: "Mock 商品目录保存
 * SKU、名称、单价、币种和可售状态").
 *
 * <p>Registered only for the {@code demo} profile when {@code order.adapters.product=mock}. The
 * catalog is in-memory and is not suitable for production. Unknown SKUs throw explicitly.
 */
@Component
@Profile("demo")
@ConditionalOnProperty(name = "order.adapters.product", havingValue = "mock")
public class ProductCatalogClientImpl implements ProductCatalogClient {
  private static final Map<String, ProductSnapshot> DEFAULT_CATALOG =
      Map.of(
          "SKU-001",
              new ProductSnapshot("SKU-001", "Wireless Mouse", new BigDecimal("99.00"), "CNY"),
          "SKU-002",
              new ProductSnapshot(
                  "SKU-002", "Mechanical Keyboard", new BigDecimal("399.00"), "CNY"),
          "SKU-003",
              new ProductSnapshot("SKU-003", "USB-C Cable 2m", new BigDecimal("29.00"), "CNY"));
  private final Map<String, ProductSnapshot> catalog = new ConcurrentHashMap<>(DEFAULT_CATALOG);
  private final Set<String> unavailable = ConcurrentHashMap.newKeySet();
  private MockFailureRegistry failureRegistry = new MockFailureRegistry();

  @Autowired
  void setFailureRegistry(MockFailureRegistry failureRegistry) {
    this.failureRegistry = failureRegistry;
  }

  @Override
  public List<ProductSnapshot> getSaleableProducts(Set<String> skuIds) {
    failureRegistry.check("product");
    List<ProductSnapshot> found = new ArrayList<>(skuIds.size());
    for (String skuId : skuIds) {
      ProductSnapshot snapshot = catalog.get(skuId);
      if (snapshot == null || unavailable.contains(skuId)) {
        throw new ProductNotFoundException("SKU '%s' is not available".formatted(skuId));
      }
      found.add(snapshot);
    }
    return found;
  }

  public void setProduct(
      String skuId, String productName, BigDecimal unitPrice, String currency, boolean saleable) {
    if (unitPrice == null || unitPrice.signum() < 0 || unitPrice.scale() > 2) {
      throw new InvalidRequestException(
          "Product price must be non-negative with at most 2 decimals");
    }
    if (currency == null || currency.length() != 3) {
      throw new InvalidRequestException("Product currency must be a 3-letter ISO code");
    }
    catalog.put(skuId, new ProductSnapshot(skuId, productName, unitPrice, currency));
    if (saleable) {
      unavailable.remove(skuId);
    } else {
      unavailable.add(skuId);
    }
  }
}
