package org.acm.kb.interfaces.http.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddEvalCaseRequest {
  @NotBlank private String query;
  private String expectedAnswer;
}
