package org.acm.ca.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.acm.ca.application.port.out.KbSearchClient;
import org.acm.ca.application.port.out.KbSearchClient.KbChunk;
import org.acm.ca.application.port.out.KbSearchClient.KbSummary;
import org.acm.ca.application.port.out.KbSearchClient.SearchRequest;
import org.acm.ca.application.rule.ReplyRulesConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KbSearchToolTest {

  private KbSearchClient kbSearchClient;
  private KbSearchTool tool;

  @BeforeEach
  void setUp() {
    kbSearchClient = mock(KbSearchClient.class);
    ReplyRulesConfig config = new ReplyRulesConfig("default", 5, List.of());
    tool = new KbSearchTool(kbSearchClient, config);
  }

  @Test
  void formatsChunksWhenFound() {
    when(kbSearchClient.search(any()))
        .thenReturn(List.of(new KbChunk("退款政策内容", 0.9, "DOC-1", "退款政策.md")));
    String result = tool.searchKnowledgeBase("退款", "KB-1");
    assertThat(result).contains("退款政策内容").contains("退款政策.md");
  }

  @Test
  void passesTargetKbToSearch() {
    when(kbSearchClient.search(any())).thenReturn(List.of());
    tool.searchKnowledgeBase("退款", "KB-2");
    verify(kbSearchClient).search(new SearchRequest("KB-2", "退款", 5));
  }

  @Test
  void returnsEmptyMessageWhenNoChunks() {
    when(kbSearchClient.search(any())).thenReturn(List.of());
    assertThat(tool.searchKnowledgeBase("退款", "KB-1")).contains("未找到");
  }

  @Test
  void returnsErrorMessageWhenSearchFails() {
    when(kbSearchClient.search(any())).thenThrow(new RuntimeException("连接失败"));
    assertThat(tool.searchKnowledgeBase("退款", "KB-1")).contains("知识库检索失败");
  }

  @Test
  void blankKbNoReturnsGuidanceInsteadOfSearching() {
    assertThat(tool.searchKnowledgeBase("退款", "  ")).contains("listKnowledgeBases");
  }

  @Test
  void listsActiveKnowledgeBases() {
    when(kbSearchClient.listActive())
        .thenReturn(List.of(new KbSummary("KB-1", "退款政策", 3), new KbSummary("KB-2", "发票规则", 5)));
    assertThat(tool.listKnowledgeBases())
        .contains("KB-1")
        .contains("退款政策")
        .contains("KB-2")
        .contains("发票规则");
  }

  @Test
  void catalogTextFallsBackToEmptyWhenListFails() {
    when(kbSearchClient.listActive()).thenThrow(new RuntimeException("连接失败"));
    assertThat(tool.catalogText()).isEmpty();
  }
}
