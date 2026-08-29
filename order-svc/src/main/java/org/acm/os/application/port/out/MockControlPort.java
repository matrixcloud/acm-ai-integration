package org.acm.os.application.port.out;

import java.math.BigDecimal;

public interface MockControlPort {
  void setProduct(
      String skuId,
      String productName,
      BigDecimal unitPrice,
      String currency,
      boolean saleable);

  void setInventory(String skuId, int quantity);

  void failNext(String capability);
}
