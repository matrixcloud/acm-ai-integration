package org.acm.ca.application.rule;

/**
 * A reply rule: comma-separated keywords, a dedicated system prompt, and priority (higher wins).
 * {@code name} is a stable low-cardinality identifier used in observability and logging.
 */
public record ReplyRule(String name, String keywords, String systemPrompt, int priority) {}
