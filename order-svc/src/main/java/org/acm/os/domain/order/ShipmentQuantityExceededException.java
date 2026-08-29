package org.acm.os.domain.order;

import org.acm.os.domain.shared.BusinessException;

public class ShipmentQuantityExceededException extends BusinessException {
  public ShipmentQuantityExceededException(String message) {
    super("SHIPMENT_QUANTITY_EXCEEDED", message);
  }
}
