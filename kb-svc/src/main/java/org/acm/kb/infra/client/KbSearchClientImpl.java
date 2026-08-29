package org.acm.kb.infra.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.acm.kb.application.port.out.KbSearchClient;
import org.acm.kb.application.port.out.KbSearchClient.KbChunk;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Mock implementation of {@link KbSearchClient} for the {@code demo} profile.
 *
 * <p>Returns canned retrieval chunks keyed by query keywords. This adapter exists so {@code
 * customer-agent} can call the knowledge-base search port without a real HTTP integration in the
 * current demo phase.
 */
@Component
public class KbSearchClientImpl implements KbSearchClient {

  private final Map<String, String> mockChunks = new ConcurrentHashMap<>();

  public KbSearchClientImpl() {
    mockChunks.put(
        "退款",
        "退款申请需在订单签收后 7 天内提交，审核通过后将原路退回支付账户。");
    mockChunks.put(
        "退货",
        "如需退货，请在确认收货后 7 天内发起退货申请，商品需保持原包装完好。");
    mockChunks.put(
        "发票",
        "电子发票会在订单完成后 24 小时内发送至您的注册邮箱，可在订单详情页下载。");
    mockChunks.put(
        "物流",
        "您可以在订单详情页查看实时物流信息，通常 1-3 个工作日送达。");
  }

  @Override
  public List<KbChunk> search(SearchRequest request) {
    String query = request.query();
    for (Map.Entry<String, String> entry : mockChunks.entrySet()) {
      if (query != null && query.contains(entry.getKey())) {
        return List.of(
            new KbChunk(entry.getValue(), 0.85, "DOC-MOCK-0001", "知识库文档.txt"));
      }
    }
    return List.of(
        new KbChunk("暂无与该查询直接相关的文档内容。", 0.0, "DOC-MOCK-0001", "知识库文档.txt"));
  }
}
