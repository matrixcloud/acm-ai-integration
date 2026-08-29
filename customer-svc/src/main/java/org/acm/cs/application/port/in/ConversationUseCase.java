package org.acm.cs.application.port.in;

import jakarta.validation.Valid;
import java.util.List;
import org.acm.cs.application.port.in.command.CreateConversationCommand;
import org.acm.cs.application.port.in.command.SendMessageCommand;
import org.acm.cs.application.port.in.command.SubmitFeedbackCommand;
import org.acm.cs.application.port.in.query.SearchConversationQuery;
import org.acm.cs.domain.conversation.Conversation;
import org.acm.cs.domain.conversation.Message;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;

@Validated
public interface ConversationUseCase {

  Conversation create(@Valid CreateConversationCommand command, String idempotencyKey);

  MessageThread sendMessage(@Valid SendMessageCommand command, String idempotencyKey);

  Conversation endConversation(String conversationNo, String idempotencyKey);

  Conversation submitFeedback(@Valid SubmitFeedbackCommand command, String idempotencyKey);

  Conversation findByConversationNo(String conversationNo);

  Page<Conversation> search(@Valid SearchConversationQuery query);

  List<QuickQuestionItem> listQuickQuestions();

  record MessageThread(String conversationNo, List<Message> messages) {}

  record QuickQuestionItem(Long id, Integer sortOrder, String questionText) {}
}
