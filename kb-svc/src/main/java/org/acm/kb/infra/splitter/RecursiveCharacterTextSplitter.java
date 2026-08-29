package org.acm.kb.infra.splitter;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.transformer.splitter.TextSplitter;

/**
 * Recursive character text splitter that mirrors LangChain's recursive separator-chain algorithm.
 *
 * <p>Splits text by trying separators in priority order ({@code "\n\n"} &rarr; {@code "\n"} &rarr;
 * {@code "。"} &rarr; {@code "."} &rarr; {@code " "} &rarr; {@code ""}). Each fragment larger than
 * {@code chunkSize} is recursively split with the next separator; fragments are merged back up to
 * {@code chunkSize} while keeping {@code chunkOverlap} characters of context between adjacent
 * chunks. Unlike {@code TokenTextSplitter}, sizing is by character count and does not depend on a
 * tokenizer.
 */
public class RecursiveCharacterTextSplitter extends TextSplitter {

  private final int chunkSize;
  private final int chunkOverlap;
  private final List<String> separators;

  private RecursiveCharacterTextSplitter(int chunkSize, int chunkOverlap, List<String> separators) {
    this.chunkSize = chunkSize;
    this.chunkOverlap = chunkOverlap;
    this.separators = separators;
  }

  /**
   * Returns a builder for configuring {@code chunkSize}, {@code chunkOverlap}, and {@code
   * separators}.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  @Override
  protected List<String> splitText(String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }
    if (text.length() <= chunkSize) {
      return List.of(text);
    }
    return splitTextWithSeparators(text, 0);
  }

  private List<String> splitTextWithSeparators(String text, int separatorIndex) {
    if (text.length() <= chunkSize) {
      return List.of(text);
    }
    if (separatorIndex >= separators.size()) {
      return splitByFixedSize(text);
    }
    String separator = separators.get(separatorIndex);
    if (separator.isEmpty()) {
      return splitByFixedSize(text);
    }
    if (!text.contains(separator)) {
      return splitTextWithSeparators(text, separatorIndex + 1);
    }
    String[] parts = text.split(java.util.regex.Pattern.quote(separator), -1);
    List<String> finalChunks = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String part : parts) {
      String piece = part;
      if (piece.length() > chunkSize) {
        if (current.length() > 0) {
          finalChunks.add(current.toString());
          current.setLength(0);
        }
        finalChunks.addAll(splitTextWithSeparators(piece, separatorIndex + 1));
        continue;
      }
      String candidate = current.length() > 0 ? current + separator + piece : piece;
      if (candidate.length() <= chunkSize) {
        current.setLength(0);
        current.append(candidate);
      } else {
        if (current.length() > 0) {
          finalChunks.add(current.toString());
          String overlap = current.substring(Math.max(0, current.length() - chunkOverlap));
          current.setLength(0);
          current.append(overlap).append(separator).append(piece);
        } else {
          finalChunks.add(piece);
        }
      }
    }
    if (current.length() > 0) {
      finalChunks.add(current.toString());
    }
    return finalChunks;
  }

  private List<String> splitByFixedSize(String text) {
    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(start + chunkSize, text.length());
      chunks.add(text.substring(start, end));
      if (end == text.length()) {
        break;
      }
      start = end - chunkOverlap;
      if (start < 0) {
        start = 0;
      }
    }
    return chunks;
  }

  /** Builder for {@link RecursiveCharacterTextSplitter}. */
  public static final class Builder {
    private int chunkSize = 1000;
    private int chunkOverlap = 200;
    private List<String> separators = List.of("\n\n", "\n", "。", ".", " ", "");

    private Builder() {}

    /**
     * Sets the maximum chunk size in characters.
     *
     * @param chunkSize maximum characters per chunk
     * @return this builder
     */
    public Builder chunkSize(int chunkSize) {
      this.chunkSize = chunkSize;
      return this;
    }

    /**
     * Sets the overlap between adjacent chunks in characters.
     *
     * @param chunkOverlap overlap character count
     * @return this builder
     */
    public Builder chunkOverlap(int chunkOverlap) {
      this.chunkOverlap = chunkOverlap;
      return this;
    }

    /**
     * Sets the recursive separator chain (highest priority first).
     *
     * @param separators ordered list of separators
     * @return this builder
     */
    public Builder separators(List<String> separators) {
      this.separators = separators;
      return this;
    }

    /**
     * Builds the splitter.
     *
     * @return a new {@link RecursiveCharacterTextSplitter}
     */
    public RecursiveCharacterTextSplitter build() {
      if (chunkSize <= 0) {
        throw new IllegalArgumentException("chunkSize must be greater than 0");
      }
      if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
        throw new IllegalArgumentException(
            "chunkOverlap must be in [0, chunkSize), got %d for chunkSize %d"
                .formatted(chunkOverlap, chunkSize));
      }
      return new RecursiveCharacterTextSplitter(chunkSize, chunkOverlap, separators);
    }
  }
}
