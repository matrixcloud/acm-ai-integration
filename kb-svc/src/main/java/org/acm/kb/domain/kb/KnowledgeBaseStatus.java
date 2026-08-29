package org.acm.kb.domain.kb;

/**
 * Lifecycle states of a knowledge base.
 *
 * <p>Only {@link #ACTIVE} knowledge bases accept new document uploads; both states allow search.
 */
public enum KnowledgeBaseStatus {
  ACTIVE,
  ARCHIVED
}
