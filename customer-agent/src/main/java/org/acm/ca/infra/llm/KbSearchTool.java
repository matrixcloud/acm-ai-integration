package org.acm.ca.infra.llm;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.acm.ca.application.port.out.KbSearchClient;
import org.acm.ca.application.port.out.KbSearchClient.KbChunk;
import org.acm.ca.application.port.out.KbSearchClient.KbSummary;
import org.acm.ca.application.port.out.KbSearchClient.SearchRequest;
import org.acm.ca.application.rule.ReplyRulesConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Knowledge-base retrieval tools exposed to the ReAct loop. The target knowledge base is chosen
 * dynamically by the LLM via the {@code kbNo} parameter; {@link #listKnowledgeBases()} exposes the
 * catalog of active knowledge bases to guide that choice. Tool failures are converted to an error
 * message for the LLM to reason about, rather than propagated as exceptions.
 */
@Component
public class KbSearchTool {

  private static final Logger log = LoggerFactory.getLogger(KbSearchTool.class);

  private static final Duration CATALOG_TTL = Duration.ofSeconds(60);

  private final KbSearchClient kbSearchClient;
  private final int topK;

  private volatile List<KbSummary> catalogCache = List.of();
  private volatile long catalogFetchedAt;

  public KbSearchTool(KbSearchClient kbSearchClient, ReplyRulesConfig config) {
    this.kbSearchClient = kbSearchClient;
    this.topK = config.kbTopK();
  }

  @Tool(
      description =
          "搜索知识库获取相关文档内容。必须指定 kbNo 为目标知识库编号，其取值见 listKnowledgeBases 返回的清单；"
              + "不确定查哪个库时先调用 listKnowledgeBases。当客户询问退款政策、退货规则、发票申请、发货时间、平台规则等需要查阅知识库的问题时使用此工具。"
              + "对于订单状态查询等可从已有订单信息直接回答的问题，无需使用此工具。")
  public String searchKnowledgeBase(
      @ToolParam(description = "搜索查询文本，应为客户问题的关键词或核心内容") String query,
      @ToolParam(description = "目标知识库编号，取值见 listKnowledgeBases 清单") String kbNo) {
    if (kbNo == null || kbNo.isBlank()) {
      return "未指定知识库，请先调用 listKnowledgeBases 获取可用知识库编号后再搜索。";
    }
    try {
      List<KbChunk> chunks = kbSearchClient.search(new SearchRequest(kbNo, query, topK));
      if (chunks.isEmpty()) {
        return "未找到与查询相关的知识库内容。";
      }
      return chunks.stream()
          .map(c -> "- " + c.content() + "（来源：" + c.documentName() + "）")
          .collect(Collectors.joining("\n"));
    } catch (Exception e) {
      log.warn("llm.tool op=searchKnowledgeBase kbNo={} query={} failed", kbNo, query, e);
      return "知识库检索失败：" + e.getMessage() + "。请基于已有信息回答或建议客户稍后重试。";
    }
  }

  @Tool(
      description = "列出当前可用的知识库（编号、名称、文档数），用于确定 searchKnowledgeBase 的 kbNo 参数。" + "不确定查哪个库时先调用此工具。")
  public String listKnowledgeBases() {
    String catalog = catalogText();
    return catalog.isBlank() ? "当前没有可用的知识库清单，请基于已有信息回答。" : catalog;
  }

  /**
   * Returns the active knowledge-base catalog as prompt text with a 60s cache. Never throws:
   * retrieval failures fall back to the cached catalog or an empty string.
   */
  public String catalogText() {
    List<KbSummary> active = cachedActiveKbs();
    if (active.isEmpty()) {
      return "";
    }
    return active.stream()
        .map(kb -> "- " + kb.kbNo() + "：" + kb.name() + "（" + kb.docCount() + " 篇文档）")
        .collect(Collectors.joining("\n"));
  }

  private List<KbSummary> cachedActiveKbs() {
    long now = System.nanoTime();
    if (!catalogCache.isEmpty() && now - catalogFetchedAt < CATALOG_TTL.toNanos()) {
      return catalogCache;
    }
    try {
      List<KbSummary> fresh = kbSearchClient.listActive();
      catalogCache = fresh;
      catalogFetchedAt = now;
      return fresh;
    } catch (Exception e) {
      log.warn("llm.tool op=listKnowledgeBases catalogRefresh failed, serving stale cache", e);
      return catalogCache;
    }
  }
}
