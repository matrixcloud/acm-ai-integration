package org.acm.kb.application.port.in.command;

/** Command to add a test case to an evaluation suite. */
public record AddEvalCaseCommand(String suiteNo, String query, String expectedAnswer) {}
