package org.acm.kb.interfaces.http.response;

import java.util.List;
import lombok.Data;

@Data
public class SearchResponse {
  private List<ChunkResponse> chunks;

  @Data
  public static class ChunkResponse {
    private String content;
    private double score;
    private String documentNo;
    private String documentName;
  }
}
