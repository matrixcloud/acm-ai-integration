package org.acm.kb.interfaces.http.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateKbRequest {
  @NotBlank private String name;
}
