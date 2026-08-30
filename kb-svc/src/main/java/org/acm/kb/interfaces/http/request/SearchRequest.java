package org.acm.kb.interfaces.http.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SearchRequest {
  @NotBlank private String query;

  @Min(1)
  private int topK = 5;
}
