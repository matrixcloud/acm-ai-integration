package org.acm.kb.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.acm.kb.domain.kb.Document;
import org.acm.kb.domain.kb.DocumentChunkRepository;
import org.acm.kb.domain.kb.DocumentRepository;
import org.acm.kb.domain.kb.KnowledgeBase;
import org.acm.kb.domain.kb.KnowledgeBaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class KbServiceTest {

  @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
  @Mock private DocumentRepository documentRepository;
  @Mock private DocumentChunkRepository documentChunkRepository;
  @Mock private VectorStore vectorStore;
  @Mock private TextSplitter textSplitter;
  @Mock private TransactionTemplate transactionTemplate;

  @InjectMocks private KbService service;

  @Test
  void uploadDocumentBatchesEmbeddingIntoChunksOfAtMostTen() throws Exception {
    KnowledgeBase kb = KnowledgeBase.create("知识库");
    when(knowledgeBaseRepository.findByKbNo("KB-1")).thenReturn(Optional.of(kb));
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getBytes()).thenReturn("内容".getBytes(StandardCharsets.UTF_8));
    when(file.getOriginalFilename()).thenReturn("test.txt");

    List<org.springframework.ai.document.Document> chunks = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      chunks.add(new org.springframework.ai.document.Document("chunk-" + i, Map.of()));
    }
    when(textSplitter.apply(anyList())).thenReturn(chunks);

    service.uploadDocument("KB-1", file);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<org.springframework.ai.document.Document>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(vectorStore, times(3)).add(captor.capture());
    assertThat(captor.getAllValues().get(0)).hasSize(10);
    assertThat(captor.getAllValues().get(1)).hasSize(10);
    assertThat(captor.getAllValues().get(2)).hasSize(5);
  }

  @Test
  void uploadMarkdownPreservesUtf8Content() throws Exception {
    KnowledgeBase kb = KnowledgeBase.create("售后退换货");
    when(knowledgeBaseRepository.findByKbNo("KB-1")).thenReturn(Optional.of(kb));
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getBytes()).thenReturn("# 退款\n退款通常 1-7 个工作日到账。".getBytes(StandardCharsets.UTF_8));
    when(file.getOriginalFilename()).thenReturn("售后退换货.md");
    when(textSplitter.apply(anyList()))
        .thenAnswer(invocation -> new ArrayList<>(invocation.getArgument(0)));

    service.uploadDocument("KB-1", file);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<org.springframework.ai.document.Document>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(vectorStore).add(captor.capture());
    assertThat(captor.getValue())
        .extracting(org.springframework.ai.document.Document::getText)
        .containsExactly("退款通常 1-7 个工作日到账。");
  }
}
