package org.acm.cs.application.port.in.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageCommand {
  @NotBlank private String conversationNo;
  @NotBlank private String content;
}
