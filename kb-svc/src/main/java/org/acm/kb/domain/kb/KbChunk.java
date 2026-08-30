package org.acm.kb.domain.kb;

/** A single retrieved chunk from a knowledge-base similarity search. */
public record KbChunk(String content, double score, String documentNo, String documentName) {}
