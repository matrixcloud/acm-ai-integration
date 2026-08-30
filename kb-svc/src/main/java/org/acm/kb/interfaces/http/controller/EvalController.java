package org.acm.kb.interfaces.http.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.acm.kb.application.port.in.EvaluationUseCase;
import org.acm.kb.application.port.in.EvaluationUseCase.RunReport;
import org.acm.kb.application.port.in.EvaluationUseCase.SuiteSummary;
import org.acm.kb.domain.eval.EvaluationRun;
import org.acm.kb.domain.eval.EvaluationSuite;
import org.acm.kb.interfaces.http.mapper.EvalResponseMapper;
import org.acm.kb.interfaces.http.request.AddEvalCaseRequest;
import org.acm.kb.interfaces.http.request.CreateEvalSuiteRequest;
import org.acm.kb.interfaces.http.request.StartEvalRunRequest;
import org.acm.kb.interfaces.http.response.EvalRunResponse;
import org.acm.kb.interfaces.http.response.EvalSuiteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for evaluation test-suite management and batch evaluation runs. */
@RestController
@RequestMapping("/eval")
@RequiredArgsConstructor
public class EvalController {

  private final EvaluationUseCase evaluationUseCase;
  private final EvalResponseMapper responseMapper;

  @PostMapping("/suites")
  public ResponseEntity<EvalSuiteResponse> createSuite(
      @Valid @RequestBody CreateEvalSuiteRequest request) {
    EvaluationSuite suite = evaluationUseCase.createSuite(request.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(responseMapper.toSuiteResponse(suite, 0));
  }

  @GetMapping("/suites")
  public List<EvalSuiteResponse> listSuites() {
    return evaluationUseCase.listSuites().stream()
        .map(summary -> responseMapper.toSuiteResponse(summary.suite(), summary.caseCount()))
        .toList();
  }

  @PostMapping("/suites/{suiteNo}/cases")
  public ResponseEntity<EvalSuiteResponse> addCase(
      @PathVariable String suiteNo, @Valid @RequestBody AddEvalCaseRequest request) {
    SuiteSummary summary =
        evaluationUseCase.addCase(suiteNo, request.getQuery(), request.getExpectedAnswer());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseMapper.toSuiteResponse(summary.suite(), summary.caseCount()));
  }

  @PostMapping("/runs")
  public ResponseEntity<EvalRunResponse> startRun(@Valid @RequestBody StartEvalRunRequest request) {
    EvaluationRun run =
        evaluationUseCase.startRun(request.getKbNo(), request.getSuiteNo(), request.getTopK());
    RunReport report = evaluationUseCase.getRun(run.getRunNo());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseMapper.toRunResponse(report.run(), report.details()));
  }

  @GetMapping("/runs/{runNo}")
  public EvalRunResponse getRun(@PathVariable String runNo) {
    RunReport report = evaluationUseCase.getRun(runNo);
    return responseMapper.toRunResponse(report.run(), report.details());
  }
}
