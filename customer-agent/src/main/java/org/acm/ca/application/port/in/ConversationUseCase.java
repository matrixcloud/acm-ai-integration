package org.acm.ca.application.port.in;

import jakarta.validation.Valid;
import java.util.List;
import org.acm.ca.application.port.in.command.CreateConversationCommand;
import org.acm.ca.application.port.in.command.SendMessageCommand;
import org.acm.ca.application.port.in.command.SubmitFeedbackCommand;
import org.acm.ca.application.port.in.query.SearchConversationQuery;
import org.acm.ca.domain.conversation.Conversation;
import org.acm.ca.domain.conversation.Message;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;

@Validated
public interface ConversationUseCase {

  Conversation create(@Valid CreateConversationCommand command, String idempotencyKey);

  void streamMessage(
      @Valid SendMessageCommand command, String idempotencyKey, ConversationStream stream);

  Conversation endConversation(String conversationNo, String idempotencyKey);

  Conversation submitFeedback(@Valid SubmitFeedbackCommand command, String idempotencyKey);

  Conversation findByConversationNo(String conversationNo);

  Page<Conversation> search(@Valid SearchConversationQuery query);

  List<QuickQuestionItem> listQuickQuestions();

  record MessageThread(String conversationNo, List<Message> messages) {}

  record QuickQuestionItem(Long id, Integer sortOrder, String questionText) {}
}
