package org.acm.kb.interfaces.http.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class EvalSuiteResponse {
  private String suiteNo;
  private String name;
  private int caseCount;
  private LocalDateTime createdAt;
}
