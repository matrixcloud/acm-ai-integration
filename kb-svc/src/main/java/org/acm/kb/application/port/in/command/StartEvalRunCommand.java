package org.acm.kb.application.port.in.command;

/** Command to start a batch evaluation run. */
public record StartEvalRunCommand(String kbNo, String suiteNo, int topK) {}
