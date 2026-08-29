package org.acm.kb.application.port.in.command;

/** Command to perform a similarity search against a knowledge base. */
public record SearchCommand(String kbNo, String query, int topK) {}
