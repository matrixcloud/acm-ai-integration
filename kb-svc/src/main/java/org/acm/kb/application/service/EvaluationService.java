package org.acm.kb.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.acm.kb.application.port.in.EvaluationUseCase;
import org.acm.kb.application.port.in.EvaluationUseCase.RunReport;
import org.acm.kb.application.port.in.EvaluationUseCase.SuiteSummary;
import org.acm.kb.application.port.in.command.SearchCommand;
import org.acm.kb.application.port.out.KbSearchClient.KbChunk;
import org.acm.kb.domain.eval.EvaluationCase;
import org.acm.kb.domain.eval.EvaluationCaseRepository;
import org.acm.kb.domain.eval.EvaluationMetrics;
import org.acm.kb.domain.eval.EvaluationRun;
import org.acm.kb.domain.eval.EvaluationRunDetail;
import org.acm.kb.domain.eval.EvaluationRunDetailRepository;
import org.acm.kb.domain.eval.EvaluationRunNotFoundException;
import org.acm.kb.domain.eval.EvaluationRunRepository;
import org.acm.kb.domain.eval.EvaluationSuite;
import org.acm.kb.domain.eval.EvaluationSuiteNotFoundException;
import org.acm.kb.domain.eval.EvaluationSuiteRepository;
import org.acm.kb.domain.shared.InvalidRequestException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link EvaluationUseCase} implementation.
 *
 * <p>Orchestrates the offline batch evaluation pipeline: for each test case in a suite, retrieve
 * context via {@link KbService}, generate an answer with the evaluation {@link ChatClient}, then
 * score with three evaluators (context relevancy, faithfulness, answer relevancy). Aggregates
 * per-case scores into run-level averages and pass rates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService implements EvaluationUseCase {

  private static final String ANSWER_PROMPT_TEMPLATE =
      "根据以下检索到的上下文回答问题。如果上下文中没有相关信息，请说明无法回答。\n\n上下文：\n%s\n\n问题：\n%s";

  private final EvaluationSuiteRepository suiteRepository;
  private final EvaluationCaseRepository caseRepository;
  private final EvaluationRunRepository runRepository;
  private final EvaluationRunDetailRepository detailRepository;
  private final KbService kbService;
  private final ChatClient.Builder chatClientBuilder;
  private final Evaluator contextRelevancyEvaluator;
  private final Evaluator faithfulnessEvaluator;
  private final Evaluator answerRelevancyEvaluator;

  @Override
  @Transactional
  public EvaluationSuite createSuite(String name) {
    EvaluationSuite suite = EvaluationSuite.create(name);
    return suiteRepository.save(suite);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SuiteSummary> listSuites() {
    return suiteRepository.findAll().stream()
        .map(suite -> new SuiteSummary(suite, (int) caseRepository.countBySuiteId(suite.getId())))
        .toList();
  }

  @Override
  @Transactional
  public SuiteSummary addCase(String suiteNo, String query, String expectedAnswer) {
    EvaluationSuite suite = loadSuite(suiteNo);
    long existingCount = caseRepository.countBySuiteId(suite.getId());
    EvaluationCase caseEntity =
        EvaluationCase.of(suite.getId(), (int) (existingCount + 1), query, expectedAnswer);
    caseRepository.save(caseEntity);
    return new SuiteSummary(suite, (int) (existingCount + 1));
  }

  @Override
  public EvaluationRun startRun(String kbNo, String suiteNo, int topK) {
    if (topK <= 0) {
      throw new InvalidRequestException("topK must be greater than 0");
    }
    EvaluationSuite suite = loadSuite(suiteNo);
    List<EvaluationCase> cases = caseRepository.findBySuiteId(suite.getId());
    if (cases.isEmpty()) {
      throw new InvalidRequestException("Evaluation suite %s has no test cases".formatted(suiteNo));
    }
    EvaluationRun run = EvaluationRun.create(kbNo, suite.getId(), topK);
    runRepository.save(run);
    List<EvaluationRunDetail> details = new ArrayList<>();
    try {
      for (EvaluationCase caseEntity : cases) {
        EvaluationRunDetail detail = evaluateCase(run, caseEntity, kbNo, topK);
        details.add(detail);
        detailRepository.save(detail);
      }
      EvaluationMetrics metrics = aggregate(details);
      run.markCompleted(metrics);
      runRepository.save(run);
      return run;
    } catch (RuntimeException e) {
      log.error("Evaluation run {} failed", run.getRunNo(), e);
      run.markFailed();
      runRepository.save(run);
      throw e;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public RunReport getRun(String runNo) {
    EvaluationRun run =
        runRepository
            .findByRunNo(runNo)
            .orElseThrow(
                () ->
                    new EvaluationRunNotFoundException(
                        "Evaluation run %s not found".formatted(runNo)));
    List<EvaluationRunDetail> details = detailRepository.findByRunId(run.getId());
    return new RunReport(run, details);
  }

  private EvaluationRunDetail evaluateCase(
      EvaluationRun run, EvaluationCase caseEntity, String kbNo, int topK) {
    List<KbChunk> chunks = kbService.search(new SearchCommand(kbNo, caseEntity.getQuery(), topK));
    List<Document> contextDocuments =
        chunks.stream()
            .map(chunk -> Document.builder().text(chunk.content()).build())
            .collect(Collectors.toList());
    String contextText = chunks.stream().map(KbChunk::content).collect(Collectors.joining("\n\n"));
    String answer = generateAnswer(caseEntity.getQuery(), contextText);
    EvaluationRequest contextRequest =
        new EvaluationRequest(caseEntity.getQuery(), contextDocuments, "");
    EvaluationResponse contextResponse = contextRelevancyEvaluator.evaluate(contextRequest);
    EvaluationRequest faithfulnessRequest =
        new EvaluationRequest(caseEntity.getQuery(), contextDocuments, answer);
    EvaluationResponse faithfulnessResponse = faithfulnessEvaluator.evaluate(faithfulnessRequest);
    EvaluationRequest relevancyRequest =
        new EvaluationRequest(caseEntity.getQuery(), contextDocuments, answer);
    EvaluationResponse relevancyResponse = answerRelevancyEvaluator.evaluate(relevancyRequest);
    return EvaluationRunDetail.of(
        run.getId(),
        caseEntity.getQuery(),
        answer,
        contextResponse.getScore(),
        faithfulnessResponse.getScore(),
        relevancyResponse.getScore());
  }

  private String generateAnswer(String query, String context) {
    String prompt = ANSWER_PROMPT_TEMPLATE.formatted(context, query);
    return chatClientBuilder.build().prompt().user(prompt).call().content();
  }

  private EvaluationMetrics aggregate(List<EvaluationRunDetail> details) {
    int total = details.size();
    if (total == 0) {
      return new EvaluationMetrics(0, 0, 0, 0, 0, 0);
    }
    double contextSum = 0;
    double faithSum = 0;
    double answerSum = 0;
    for (EvaluationRunDetail detail : details) {
      contextSum += detail.getContextRelevancyScore();
      faithSum += detail.getFaithfulnessScore();
      answerSum += detail.getAnswerRelevancyScore();
    }
    double contextAvg = contextSum / total;
    double faithAvg = faithSum / total;
    double answerAvg = answerSum / total;
    return new EvaluationMetrics(contextAvg, faithAvg, answerAvg, contextAvg, faithAvg, answerAvg);
  }

  private EvaluationSuite loadSuite(String suiteNo) {
    return suiteRepository
        .findBySuiteNo(suiteNo)
        .orElseThrow(
            () ->
                new EvaluationSuiteNotFoundException(
                    "Evaluation suite %s not found".formatted(suiteNo)));
  }
}
