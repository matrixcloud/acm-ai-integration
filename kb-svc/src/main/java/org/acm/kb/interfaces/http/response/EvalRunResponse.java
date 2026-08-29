package org.acm.kb.interfaces.http.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class EvalRunResponse {
  private String runNo;
  private String kbNo;
  private String status;
  private int topK;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
  private Metrics metrics;
  private List<Detail> details;

  @Data
  public static class Metrics {
    private Metric contextRelevancy;
    private Metric faithfulness;
    private Metric answerRelevancy;
  }

  @Data
  public static class Metric {
    private double avgScore;
    private double passRate;
  }

  @Data
  public static class Detail {
    private String query;
    private String generatedAnswer;
    private double contextRelevancyScore;
    private double faithfulnessScore;
    private double answerRelevancyScore;
  }
}
