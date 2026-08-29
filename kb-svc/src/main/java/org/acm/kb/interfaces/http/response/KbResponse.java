package org.acm.kb.interfaces.http.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KbResponse {
  private String kbNo;
  private String name;
  private String status;
  private int docCount;
  private LocalDateTime createdAt;
}
