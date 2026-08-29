package org.acm.os.interfaces.http.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReviewRefundRequest {
  @NotBlank private String reviewer;
  @NotBlank private String comment;
}
