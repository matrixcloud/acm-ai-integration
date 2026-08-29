package org.acm.cs.interfaces.http.response;

import lombok.Data;

@Data
public class QuickQuestionResponse {
  private Long id;
  private Integer sortOrder;
  private String questionText;
}
