package org.acm.cs.interfaces.http.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageRequest {
  @NotBlank private String content;
}
