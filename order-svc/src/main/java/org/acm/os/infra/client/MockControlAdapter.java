package org.acm.os.infra.client;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.acm.os.application.port.out.MockControlPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MockControlAdapter implements MockControlPort {
  private final ProductCatalogClientImpl productCatalog;
  private final InventoryClientImpl inventory;
  private final MockFailureRegistry failures;

  @Override
  public void setProduct(
      String skuId, String productName, BigDecimal unitPrice, String currency, boolean saleable) {
    productCatalog.setProduct(skuId, productName, unitPrice, currency, saleable);
  }

  @Override
  public void setInventory(String skuId, int quantity) {
    inventory.setStock(skuId, quantity);
  }

  @Override
  public void failNext(String capability) {
    failures.failNext(capability);
  }
}
