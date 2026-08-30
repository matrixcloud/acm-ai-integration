package org.acm.kb.infra.reader;

import java.util.ArrayList;
import java.util.List;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.ListItem;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;

/**
 * Splits markdown text into {@link Document}s by replicating {@code
 * org.springframework.ai.reader.markdown.MarkdownDocumentReader} (Apache-2.0, default config)
 * semantics.
 *
 * <p>Unlike the Spring AI reader, which re-decodes resource bytes with {@code
 * Charset.defaultCharset()} via {@code InputStreamReader}, this reader takes an already-decoded
 * {@link String}, so non-ASCII content (e.g. Chinese) is preserved regardless of the JVM default
 * encoding.
 */
public class MarkdownTextReader implements DocumentReader {

  private final Parser parser = Parser.builder().build();

  private final String content;

  public MarkdownTextReader(String content) {
    this.content = content;
  }

  @Override
  public List<Document> get() {
    PropertyVisitor visitor = new PropertyVisitor();
    parser.parse(content).accept(visitor);
    return visitor.getDocuments();
  }

  private static final class PropertyVisitor extends AbstractVisitor {

    private final List<Document> documents = new ArrayList<>();

    private final List<String> currentParagraphs = new ArrayList<>();

    @SuppressWarnings("NullAway.Init") // visit(Document) runs first via accept
    private Document.Builder currentDocumentBuilder;

    @Override
    public void visit(org.commonmark.node.Document document) {
      currentDocumentBuilder = Document.builder();
      super.visit(document);
    }

    @Override
    public void visit(Heading heading) {
      buildAndFlush();
      super.visit(heading);
    }

    @Override
    public void visit(ThematicBreak thematicBreak) {
      super.visit(thematicBreak);
    }

    @Override
    public void visit(SoftLineBreak softLineBreak) {
      translateLineBreakToSpace();
      super.visit(softLineBreak);
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {
      translateLineBreakToSpace();
      super.visit(hardLineBreak);
    }

    @Override
    public void visit(ListItem listItem) {
      translateLineBreakToSpace();
      super.visit(listItem);
    }

    @Override
    public void visit(BlockQuote blockQuote) {
      buildAndFlush();
      translateLineBreakToSpace();
      currentDocumentBuilder.metadata("category", "blockquote");
      super.visit(blockQuote);
    }

    @Override
    public void visit(Code code) {
      currentParagraphs.add(code.getLiteral());
      currentDocumentBuilder.metadata("category", "code_inline");
      super.visit(code);
    }

    @Override
    public void visit(FencedCodeBlock fencedCodeBlock) {
      buildAndFlush();
      translateLineBreakToSpace();
      currentParagraphs.add(fencedCodeBlock.getLiteral());
      currentDocumentBuilder.metadata("category", "code_block");
      currentDocumentBuilder.metadata("lang", fencedCodeBlock.getInfo());
      buildAndFlush();
      super.visit(fencedCodeBlock);
    }

    @Override
    public void visit(Text text) {
      if (text.getParent() instanceof Heading heading) {
        currentDocumentBuilder
            .metadata("category", "header_%d".formatted(heading.getLevel()))
            .metadata("title", text.getLiteral());
      } else {
        currentParagraphs.add(text.getLiteral());
      }
      super.visit(text);
    }

    private List<Document> getDocuments() {
      buildAndFlush();
      return documents;
    }

    private void buildAndFlush() {
      if (!currentParagraphs.isEmpty()) {
        documents.add(currentDocumentBuilder.text(String.join("", currentParagraphs)).build());
        currentParagraphs.clear();
      }
      currentDocumentBuilder = Document.builder();
    }

    private void translateLineBreakToSpace() {
      if (!currentParagraphs.isEmpty()) {
        currentParagraphs.add(" ");
      }
    }
  }
}
