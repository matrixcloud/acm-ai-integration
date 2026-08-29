package org.acm.kb.interfaces.http.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DocumentResponse {
  private String documentNo;
  private String name;
  private String status;
  private int chunkCount;
  private LocalDateTime createdAt;
}
