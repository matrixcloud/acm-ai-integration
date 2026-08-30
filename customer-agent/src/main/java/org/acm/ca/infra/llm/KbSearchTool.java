package org.acm.ca.infra.llm;

import java.util.List;
import java.util.stream.Collectors;
import org.acm.ca.application.port.out.KbSearchClient;
import org.acm.ca.application.port.out.KbSearchClient.KbChunk;
import org.acm.ca.application.port.out.KbSearchClient.SearchRequest;
import org.acm.ca.application.rule.ReplyRulesConfig;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Knowledge-base retrieval tool exposed to the ReAct loop. Tool failures are converted to an error
 * message for the LLM to reason about, rather than propagated as exceptions.
 */
@Component
public class KbSearchTool {

  private final KbSearchClient kbSearchClient;
  private final String kbNo;
  private final int topK;

  public KbSearchTool(KbSearchClient kbSearchClient, ReplyRulesConfig config) {
    this.kbSearchClient = kbSearchClient;
    this.kbNo = config.kbNo();
    this.topK = config.kbTopK();
  }

  @Tool(
      description =
          "搜索知识库获取相关文档内容。当客户询问退款政策、退货规则、发票申请、发货时间、平台规则等需要查阅知识库的问题时使用此工具。对于订单状态查询等可从已有订单信息直接回答的问题，无需使用此工具。")
  public String searchKnowledgeBase(
      @ToolParam(description = "搜索查询文本，应为客户问题的关键词或核心内容") String query) {
    try {
      List<KbChunk> chunks = kbSearchClient.search(new SearchRequest(kbNo, query, topK));
      if (chunks.isEmpty()) {
        return "未找到与查询相关的知识库内容。";
      }
      return chunks.stream()
          .map(c -> "- " + c.content() + "（来源：" + c.documentName() + "）")
          .collect(Collectors.joining("\n"));
    } catch (Exception e) {
      return "知识库检索失败：" + e.getMessage() + "。请基于已有信息回答或建议客户稍后重试。";
    }
  }
}
