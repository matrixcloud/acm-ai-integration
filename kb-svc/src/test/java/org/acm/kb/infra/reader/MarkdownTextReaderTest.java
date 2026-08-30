package org.acm.kb.infra.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class MarkdownTextReaderTest {

  @Test
  void splitsSectionsAtHeadingsAndKeepsHeadingOutOfContent() {
    List<Document> docs =
        new MarkdownTextReader("# 店铺\n拾光生活馆。\n\n## 客服时间\n人工客服 09:00-22:00 在线。").get();

    assertThat(docs).hasSize(2);
    assertThat(docs.get(0).getText()).isEqualTo("拾光生活馆。");
    assertThat(docs.get(1).getText()).isEqualTo("人工客服 09:00-22:00 在线。");
    assertThat(docs.get(1).getMetadata().get("category")).isEqualTo("header_2");
    assertThat(docs.get(1).getMetadata().get("title")).isEqualTo("客服时间");
  }

  @Test
  void preservesChineseContentLiterally() {
    List<Document> docs = new MarkdownTextReader("退款通常 1-7 个工作日原路到账。").get();

    assertThat(docs).hasSize(1);
    assertThat(docs.get(0).getText()).isEqualTo("退款通常 1-7 个工作日原路到账。");
  }

  @Test
  void putsCodeBlockInItsOwnDocumentWithLanguageMetadata() {
    List<Document> docs =
        new MarkdownTextReader("正文段落\n\n```java\nString s = \"中文\";\n```\n\n后续段落").get();

    assertThat(docs).hasSize(3);
    assertThat(docs.get(1).getText()).isEqualTo("String s = \"中文\";\n");
    assertThat(docs.get(1).getMetadata().get("category")).isEqualTo("code_block");
    assertThat(docs.get(1).getMetadata().get("lang")).isEqualTo("java");
    assertThat(docs.get(2).getText()).isEqualTo("后续段落");
  }

  @Test
  void putsBlockquoteInItsOwnDocument() {
    List<Document> docs = new MarkdownTextReader("正文\n\n> 引用内容\n\n结尾").get();

    assertThat(docs).hasSize(2);
    assertThat(docs.get(1).getText()).isEqualTo("引用内容结尾");
    assertThat(docs.get(1).getMetadata().get("category")).isEqualTo("blockquote");
  }

  @Test
  void doesNotSplitAtThematicBreakByDefault() {
    List<Document> docs = new MarkdownTextReader("上半段\n\n---\n\n下半段").get();

    assertThat(docs).hasSize(1);
    assertThat(docs.get(0).getText()).isEqualTo("上半段下半段");
  }
}
