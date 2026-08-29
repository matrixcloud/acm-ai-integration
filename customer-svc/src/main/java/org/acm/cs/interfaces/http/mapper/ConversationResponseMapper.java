package org.acm.cs.interfaces.http.mapper;

import java.util.List;
import org.acm.cs.application.port.in.ConversationUseCase.MessageThread;
import org.acm.cs.application.port.in.ConversationUseCase.QuickQuestionItem;
import org.acm.cs.domain.conversation.Conversation;
import org.acm.cs.domain.conversation.Message;
import org.acm.cs.interfaces.http.response.ConversationDetailResponse;
import org.acm.cs.interfaces.http.response.ConversationSummaryResponse;
import org.acm.cs.interfaces.http.response.MessageThreadResponse;
import org.acm.cs.interfaces.http.response.QuickQuestionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConversationResponseMapper {

  ConversationDetailResponse toDetailResponse(Conversation conversation);

  ConversationSummaryResponse toSummaryResponse(Conversation conversation);

  List<ConversationSummaryResponse> toSummaryResponseList(List<Conversation> conversations);

  ConversationDetailResponse.MessageResponse toMessageResponse(Message message);

  List<ConversationDetailResponse.MessageResponse> toMessageResponseList(List<Message> messages);

  default ConversationDetailResponse.FeedbackResponse toFeedbackResponse(
      org.acm.cs.domain.conversation.Feedback feedback) {
    if (feedback == null) {
      return null;
    }
    ConversationDetailResponse.FeedbackResponse response =
        new ConversationDetailResponse.FeedbackResponse();
    response.setRating(feedback.getRating().name());
    response.setComment(feedback.getComment());
    response.setSubmittedAt(feedback.getSubmittedAt());
    return response;
  }

  default MessageThreadResponse toThreadResponse(MessageThread thread) {
    MessageThreadResponse response = new MessageThreadResponse();
    response.setConversationNo(thread.conversationNo());
    response.setMessages(toMessageResponseList(thread.messages()));
    return response;
  }

  QuickQuestionResponse toQuickQuestionResponse(QuickQuestionItem quickQuestion);

  List<QuickQuestionResponse> toQuickQuestionResponseList(List<QuickQuestionItem> quickQuestions);
}
