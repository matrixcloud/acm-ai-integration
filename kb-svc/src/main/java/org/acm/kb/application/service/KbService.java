package org.acm.kb.application.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.acm.kb.application.port.in.KbUseCase;
import org.acm.kb.application.port.in.command.SearchCommand;
import org.acm.kb.application.port.out.KbSearchClient.KbChunk;
import org.acm.kb.domain.kb.Document;
import org.acm.kb.domain.kb.DocumentChunk;
import org.acm.kb.domain.kb.DocumentChunkRepository;
import org.acm.kb.domain.kb.DocumentNotFoundException;
import org.acm.kb.domain.kb.DocumentRepository;
import org.acm.kb.domain.kb.KnowledgeBase;
import org.acm.kb.domain.kb.KnowledgeBaseNotFoundException;
import org.acm.kb.domain.kb.KnowledgeBaseRepository;
import org.acm.kb.domain.shared.InvalidRequestException;
import org.acm.kb.infra.reader.MarkdownTextReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Default {@link KbUseCase} implementation.
 *
 * <p>Orchestrates the document upload pipeline: validate the knowledge base is active, persist a
 * {@code PROCESSING} document record, read the file with a type-appropriate reader (Markdown or
 * plain text), split paragraphs with the configured {@link TextSplitter}, embed and store chunks in
 * pgvector, persist chunk metadata, then mark the document {@code READY}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KbService implements KbUseCase {

  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final DocumentRepository documentRepository;
  private final DocumentChunkRepository documentChunkRepository;
  private final VectorStore vectorStore;
  private final TextSplitter textSplitter;
  private final TransactionTemplate transactionTemplate;

  // 百炼 text-embedding-v4 单次请求批上限 10，超限返回 400 batch size is invalid
  private static final int EMBEDDING_BATCH_SIZE = 10;

  @Override
  @Transactional
  public KnowledgeBase createKnowledgeBase(String name) {
    KnowledgeBase kb = KnowledgeBase.create(name);
    KnowledgeBase saved = knowledgeBaseRepository.save(kb);
    log.info("kb.created kbNo={} name={}", saved.getKbNo(), name);
    return saved;
  }

  @Override
  @Transactional(readOnly = true)
  public List<KnowledgeBase> listKnowledgeBases() {
    return knowledgeBaseRepository.findAll();
  }

  @Override
  @Transactional(readOnly = true)
  public KnowledgeBase getKnowledgeBase(String kbNo) {
    return loadKnowledgeBase(kbNo);
  }

  @Override
  public Document uploadDocument(String kbNo, MultipartFile file) {
    KnowledgeBase kb = loadKnowledgeBase(kbNo);
    kb.requireActive();
    String content = readFileContent(file);
    if (!StringUtils.hasText(content.strip())) {
      throw new InvalidRequestException("Document content must not be blank");
    }
    Document doc = Document.create(kb.getId(), file.getOriginalFilename());
    documentRepository.save(doc);
    try {
      List<org.springframework.ai.document.Document> paragraphs = readParagraphs(file, content);
      List<org.springframework.ai.document.Document> chunks = textSplitter.apply(paragraphs);
      List<DocumentChunk> savedChunks = new ArrayList<>();
      List<org.springframework.ai.document.Document> vectorDocuments = new ArrayList<>();
      int seqNo = 1;
      for (org.springframework.ai.document.Document chunk : chunks) {
        String chunkText = chunk.getText();
        if (!StringUtils.hasText(chunkText) || chunkText.isBlank()) {
          continue;
        }
        Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
        metadata.put("document_no", doc.getDocumentNo());
        metadata.put("kb_no", kb.getKbNo());
        metadata.put("document_name", doc.getName());
        metadata.put("seq_no", seqNo);
        org.springframework.ai.document.Document vectorDoc =
            org.springframework.ai.document.Document.builder()
                .text(chunkText)
                .metadata(metadata)
                .build();
        vectorDocuments.add(vectorDoc);
        savedChunks.add(DocumentChunk.of(doc.getId(), seqNo, chunkText));
        seqNo++;
      }
      for (int from = 0; from < vectorDocuments.size(); from += EMBEDDING_BATCH_SIZE) {
        int to = Math.min(from + EMBEDDING_BATCH_SIZE, vectorDocuments.size());
        vectorStore.add(vectorDocuments.subList(from, to));
      }
      transactionTemplate.executeWithoutResult(
          status -> {
            documentChunkRepository.saveAll(savedChunks);
            doc.markReady(savedChunks.size());
            documentRepository.save(doc);
            kb.incrementDocCount();
            knowledgeBaseRepository.save(kb);
          });
      log.info(
          "document.ingested documentNo={} kbNo={} chunks={}",
          doc.getDocumentNo(),
          kb.getKbNo(),
          savedChunks.size());
      return doc;
    } catch (RuntimeException e) {
      log.error(
          "document.process.failed documentNo={} kbNo={}", doc.getDocumentNo(), kb.getKbNo(), e);
      try {
        vectorStore.delete(
            new org.springframework.ai.vectorstore.filter.Filter.Expression(
                org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ,
                new org.springframework.ai.vectorstore.filter.Filter.Key("document_no"),
                new org.springframework.ai.vectorstore.filter.Filter.Value(doc.getDocumentNo())));
      } catch (RuntimeException cleanupEx) {
        log.warn("document.vector.cleanup.failed documentNo={}", doc.getDocumentNo(), cleanupEx);
      }
      doc.markFailed();
      documentRepository.save(doc);
      throw e;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<Document> listDocuments(String kbNo) {
    KnowledgeBase kb = loadKnowledgeBase(kbNo);
    return documentRepository.findByKbId(kb.getId());
  }

  @Override
  @Transactional
  public void deleteDocument(String kbNo, String docNo) {
    KnowledgeBase kb = loadKnowledgeBase(kbNo);
    Document doc =
        documentRepository
            .findByDocumentNo(docNo)
            .orElseThrow(
                () -> new DocumentNotFoundException("Document %s not found".formatted(docNo)));
    if (!doc.getKbId().equals(kb.getId())) {
      throw new DocumentNotFoundException(
          "Document %s does not belong to knowledge base %s".formatted(docNo, kbNo));
    }
    vectorStore.delete(
        new org.springframework.ai.vectorstore.filter.Filter.Expression(
            org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ,
            new org.springframework.ai.vectorstore.filter.Filter.Key("document_no"),
            new org.springframework.ai.vectorstore.filter.Filter.Value(docNo)));
    documentChunkRepository.deleteByDocumentId(doc.getId());
    documentRepository.delete(doc);
    kb.decrementDocCount();
    log.info("document.deleted documentNo={} kbNo={}", docNo, kbNo);
  }

  @Override
  @Transactional
  public KnowledgeBase archiveKnowledgeBase(String kbNo) {
    KnowledgeBase kb = loadKnowledgeBase(kbNo);
    kb.archive();
    return knowledgeBaseRepository.save(kb);
  }

  @Override
  @Transactional
  public KnowledgeBase activateKnowledgeBase(String kbNo) {
    KnowledgeBase kb = loadKnowledgeBase(kbNo);
    kb.activate();
    return knowledgeBaseRepository.save(kb);
  }

  @Override
  @Transactional(readOnly = true)
  public List<KbChunk> search(SearchCommand command) {
    loadKnowledgeBase(command.kbNo());
    if (command.topK() <= 0) {
      throw new InvalidRequestException("topK must be greater than 0");
    }
    org.springframework.ai.vectorstore.filter.Filter.Expression kbFilter =
        new org.springframework.ai.vectorstore.filter.Filter.Expression(
            org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ,
            new org.springframework.ai.vectorstore.filter.Filter.Key("kb_no"),
            new org.springframework.ai.vectorstore.filter.Filter.Value(command.kbNo()));
    SearchRequest request =
        SearchRequest.builder()
            .query(command.query())
            .topK(command.topK())
            .filterExpression(kbFilter)
            .build();
    long start = System.nanoTime();
    List<org.springframework.ai.document.Document> results = vectorStore.similaritySearch(request);
    long durationMs = (System.nanoTime() - start) / 1_000_000;
    List<KbChunk> chunks = new ArrayList<>();
    for (org.springframework.ai.document.Document result : results) {
      Map<String, Object> metadata = result.getMetadata();
      String documentNo = String.valueOf(metadata.getOrDefault("document_no", ""));
      String documentName = String.valueOf(metadata.getOrDefault("document_name", ""));
      Double rawScore = result.getScore();
      double score = rawScore != null ? rawScore : 0.0;
      chunks.add(new KbChunk(result.getText(), score, documentNo, documentName));
    }
    log.info(
        "kb.search kbNo={} topK={} results={} durationMs={}",
        command.kbNo(),
        command.topK(),
        chunks.size(),
        durationMs);
    return chunks;
  }

  private KnowledgeBase loadKnowledgeBase(String kbNo) {
    return knowledgeBaseRepository
        .findByKbNo(kbNo)
        .orElseThrow(
            () ->
                new KnowledgeBaseNotFoundException("Knowledge base %s not found".formatted(kbNo)));
  }

  private String readFileContent(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new InvalidRequestException("File must not be empty");
    }
    try {
      return new String(file.getBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Failed to read file content");
    }
  }

  private List<org.springframework.ai.document.Document> readParagraphs(
      MultipartFile file, String content) {
    String filename = file.getOriginalFilename();
    String extension =
        filename != null && filename.contains(".")
            ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
            : "txt";
    if ("md".equals(extension) || "markdown".equals(extension)) {
      return new MarkdownTextReader(content).read();
    }
    if ("txt".equals(extension)) {
      ByteArrayResource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
      return new TextReader(resource).read();
    }
    throw new InvalidRequestException(
        "Unsupported file type: only .txt and .md are allowed, got .%s".formatted(extension));
  }
}
