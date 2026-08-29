package org.acm.ca.interfaces.http.mapper;

import org.acm.ca.application.port.in.command.CreateConversationCommand;
import org.acm.ca.application.port.in.command.SubmitFeedbackCommand;
import org.acm.ca.application.port.in.query.SearchConversationQuery;
import org.acm.ca.domain.conversation.ConversationStatus;
import org.acm.ca.domain.shared.InvalidRequestException;
import org.acm.ca.interfaces.http.request.CreateConversationRequest;
import org.acm.ca.interfaces.http.request.SearchConversationRequest;
import org.acm.ca.interfaces.http.request.SubmitFeedbackRequest;
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
