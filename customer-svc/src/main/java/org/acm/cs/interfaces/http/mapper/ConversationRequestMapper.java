package org.acm.cs.interfaces.http.mapper;

import org.acm.cs.application.port.in.command.CreateConversationCommand;
import org.acm.cs.application.port.in.command.SubmitFeedbackCommand;
import org.acm.cs.application.port.in.query.SearchConversationQuery;
import org.acm.cs.domain.conversation.ConversationStatus;
import org.acm.cs.domain.shared.InvalidRequestException;
import org.acm.cs.interfaces.http.request.CreateConversationRequest;
import org.acm.cs.interfaces.http.request.SearchConversationRequest;
import org.acm.cs.interfaces.http.request.SubmitFeedbackRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConversationRequestMapper {

  CreateConversationCommand toCommand(CreateConversationRequest request);

  default SearchConversationQuery toQuery(SearchConversationRequest request) {
    SearchConversationQuery query = new SearchConversationQuery();
    query.setCustomerId(request.getCustomerId());
    query.setStatus(parseStatus(request.getStatus()));
    query.setPage(request.getPage());
    query.setSize(request.getSize());
    return query;
  }

  default SubmitFeedbackCommand toCommand(String conversationNo, SubmitFeedbackRequest request) {
    SubmitFeedbackCommand command = new SubmitFeedbackCommand();
    command.setConversationNo(conversationNo);
    command.setRating(request.getRating());
    command.setComment(request.getComment());
    return command;
  }

  default ConversationStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return ConversationStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException("Unsupported conversation status '%s'".formatted(status));
    }
  }
}
