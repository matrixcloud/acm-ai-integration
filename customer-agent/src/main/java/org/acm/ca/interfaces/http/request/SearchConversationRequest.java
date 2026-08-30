package org.acm.ca.interfaces.http.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SearchConversationRequest {
  @NotBlank private String customerId;
  private String status;

  @Min(1)
  private Integer page = 1;

  @Min(1)
  @Max(100)
  private Integer size = 20;
}
