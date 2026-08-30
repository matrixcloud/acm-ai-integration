/* (C)2025 */
package org.acm.common.persistence;

import com.github.f4b6a3.uuid.UuidCreator;
import java.io.Serializable;
import java.util.EnumSet;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

public class UUIDv7Generator implements BeforeExecutionGenerator {

  @Override
  public Serializable generate(
      SharedSessionContractImplementor session,
      Object owner,
      Object currentValue,
      EventType eventType) {
    return UuidCreator.getTimeOrdered().toString();
  }

  @Override
  public EnumSet<EventType> getEventTypes() {
    return EnumSet.of(EventType.INSERT);
  }
}
