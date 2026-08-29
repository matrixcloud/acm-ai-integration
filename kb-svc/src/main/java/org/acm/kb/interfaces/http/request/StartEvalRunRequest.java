package org.acm.kb.interfaces.http.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StartEvalRunRequest {
  @NotBlank private String kbNo;
  @NotBlank private String suiteNo;
  @Min(1) private int topK;
}
