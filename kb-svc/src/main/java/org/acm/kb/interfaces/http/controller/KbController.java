package org.acm.kb.interfaces.http.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.acm.kb.application.port.in.KbUseCase;
import org.acm.kb.application.port.in.command.SearchCommand;
import org.acm.kb.application.port.out.KbSearchClient.KbChunk;
import org.acm.kb.domain.kb.Document;
import org.acm.kb.domain.kb.KnowledgeBase;
import org.acm.kb.interfaces.http.mapper.KbRequestMapper;
import org.acm.kb.interfaces.http.mapper.KbResponseMapper;
import org.acm.kb.interfaces.http.request.CreateKbRequest;
import org.acm.kb.interfaces.http.request.SearchRequest;
import org.acm.kb.interfaces.http.response.DocumentResponse;
import org.acm.kb.interfaces.http.response.KbDetailResponse;
import org.acm.kb.interfaces.http.response.KbResponse;
import org.acm.kb.interfaces.http.response.SearchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for knowledge-base management, document upload, and similarity search.
 */
@RestController
@RequestMapping("/kbs")
@RequiredArgsConstructor
public class KbController {

  private final KbUseCase kbUseCase;
  private final KbRequestMapper requestMapper;
  private final KbResponseMapper responseMapper;

  @PostMapping
  public ResponseEntity<KbResponse> create(@Valid @RequestBody CreateKbRequest request) {
    KnowledgeBase kb = kbUseCase.createKnowledgeBase(request.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(responseMapper.toKbResponse(kb));
  }

  @GetMapping
  public List<KbResponse> list() {
    return responseMapper.toKbResponseList(kbUseCase.listKnowledgeBases());
  }

  @GetMapping("/{kbNo}")
  public KbDetailResponse get(@PathVariable String kbNo) {
    KnowledgeBase kb = kbUseCase.getKnowledgeBase(kbNo);
    List<Document> documents = kbUseCase.listDocuments(kbNo);
    return responseMapper.toDetailResponse(kb, documents);
  }

  @PostMapping("/{kbNo}/documents")
  public ResponseEntity<DocumentResponse> uploadDocument(
      @PathVariable String kbNo, @RequestParam("file") MultipartFile file) {
    Document document = kbUseCase.uploadDocument(kbNo, file);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseMapper.toDocumentResponse(document));
  }

  @GetMapping("/{kbNo}/documents")
  public List<DocumentResponse> listDocuments(@PathVariable String kbNo) {
    return responseMapper.toDocumentResponseList(kbUseCase.listDocuments(kbNo));
  }

  @DeleteMapping("/{kbNo}/documents/{docNo}")
  public ResponseEntity<Void> deleteDocument(
      @PathVariable String kbNo, @PathVariable String docNo) {
    kbUseCase.deleteDocument(kbNo, docNo);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{kbNo}/archive")
  public KbResponse archive(@PathVariable String kbNo) {
    return responseMapper.toKbResponse(kbUseCase.archiveKnowledgeBase(kbNo));
  }

  @PostMapping("/{kbNo}/activate")
  public KbResponse activate(@PathVariable String kbNo) {
    return responseMapper.toKbResponse(kbUseCase.activateKnowledgeBase(kbNo));
  }

  @PostMapping("/{kbNo}/search")
  public SearchResponse search(
      @PathVariable String kbNo, @Valid @RequestBody SearchRequest request) {
    SearchCommand command = requestMapper.toSearchCommand(kbNo, request);
    List<KbChunk> chunks = kbUseCase.search(command);
    return responseMapper.toSearchResponse(chunks);
  }
}
