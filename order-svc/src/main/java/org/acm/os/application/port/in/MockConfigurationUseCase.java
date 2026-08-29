package org.acm.os.application.port.in;

import java.math.BigDecimal;

public interface MockConfigurationUseCase {
  void setProduct(
      String skuId,
      String productName,
      BigDecimal unitPrice,
      String currency,
      boolean saleable,
      String idempotencyKey);

  void setInventory(String skuId, int quantity, String idempotencyKey);

  void failNext(String capability, String idempotencyKey);
}
