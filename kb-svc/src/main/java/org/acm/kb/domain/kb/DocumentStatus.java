package org.acm.kb.domain.kb;

/**
 * Processing states of a document within a knowledge base.
 *
 * <p>{@link #PROCESSING} is the initial state on upload; {@link #READY} marks a successfully
 * chunked and vectorized document; {@link #FAILED} marks an upload whose embedding failed.
 */
public enum DocumentStatus {
  PROCESSING,
  READY,
  FAILED
}
