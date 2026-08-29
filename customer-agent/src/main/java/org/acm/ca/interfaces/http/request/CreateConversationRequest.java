package org.acm.ca.interfaces.http.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateConversationRequest {
  @NotBlank private String customerId;
}
