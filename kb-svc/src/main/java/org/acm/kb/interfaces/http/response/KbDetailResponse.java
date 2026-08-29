package org.acm.kb.interfaces.http.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class KbDetailResponse {
  private String kbNo;
  private String name;
  private String status;
  private int docCount;
  private LocalDateTime createdAt;
  private List<DocumentResponse> documents;
}
