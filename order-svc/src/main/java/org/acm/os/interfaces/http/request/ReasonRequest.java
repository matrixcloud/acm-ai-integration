package org.acm.os.interfaces.http.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReasonRequest {
  @NotBlank private String reason;
}
