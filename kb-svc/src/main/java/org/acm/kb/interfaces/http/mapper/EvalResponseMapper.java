package org.acm.kb.interfaces.http.mapper;

import java.util.List;
import org.acm.kb.domain.eval.EvaluationRun;
import org.acm.kb.domain.eval.EvaluationRunDetail;
import org.acm.kb.domain.eval.EvaluationSuite;
import org.acm.kb.interfaces.http.response.EvalRunResponse;
import org.acm.kb.interfaces.http.response.EvalSuiteResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EvalResponseMapper {

  default EvalSuiteResponse toSuiteResponse(EvaluationSuite suite, int caseCount) {
    EvalSuiteResponse response = new EvalSuiteResponse();
    response.setSuiteNo(suite.getSuiteNo());
    response.setName(suite.getName());
    response.setCaseCount(caseCount);
    response.setCreatedAt(suite.getCreatedAt());
    return response;
  }

  default EvalRunResponse toRunResponse(EvaluationRun run, List<EvaluationRunDetail> details) {
    EvalRunResponse response = new EvalRunResponse();
    response.setRunNo(run.getRunNo());
    response.setKbNo(run.getKbNo());
    response.setStatus(run.getStatus().name());
    response.setTopK(run.getTopK());
    response.setStartedAt(run.getStartedAt());
    response.setFinishedAt(run.getFinishedAt());
    EvalRunResponse.Metrics metrics = new EvalRunResponse.Metrics();
    metrics.setContextRelevancy(toMetric(run.getContextRelevancyAvg(), run.getContextRelevancyPassRate()));
    metrics.setFaithfulness(toMetric(run.getFaithfulnessAvg(), run.getFaithfulnessPassRate()));
    metrics.setAnswerRelevancy(toMetric(run.getAnswerRelevancyAvg(), run.getAnswerRelevancyPassRate()));
    response.setMetrics(metrics);
    response.setDetails(
        details.stream()
            .map(
                detail -> {
                  EvalRunResponse.Detail d = new EvalRunResponse.Detail();
                  d.setQuery(detail.getQuery());
                  d.setGeneratedAnswer(detail.getGeneratedAnswer());
                  d.setContextRelevancyScore(detail.getContextRelevancyScore());
                  d.setFaithfulnessScore(detail.getFaithfulnessScore());
                  d.setAnswerRelevancyScore(detail.getAnswerRelevancyScore());
                  return d;
                })
            .toList());
    return response;
  }

  private EvalRunResponse.Metric toMetric(double avg, double passRate) {
    EvalRunResponse.Metric metric = new EvalRunResponse.Metric();
    metric.setAvgScore(avg);
    metric.setPassRate(passRate);
    return metric;
  }
}
