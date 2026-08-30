package org.acm.ca.interfaces.http.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.acm.ca.application.port.in.ConversationUseCase.MessageThread;
import org.acm.ca.application.port.in.ConversationUseCase.QuickQuestionItem;
import org.acm.ca.domain.conversation.Conversation;
import org.acm.ca.domain.conversation.FeedbackRating;
import org.acm.ca.interfaces.http.response.ConversationDetailResponse;
import org.acm.ca.interfaces.http.response.ConversationSummaryResponse;
import org.acm.ca.interfaces.http.response.MessageThreadResponse;
import org.acm.ca.interfaces.http.response.QuickQuestionResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ConversationResponseMapperTest {

  private final ConversationResponseMapper mapper =
      Mappers.getMapper(ConversationResponseMapper.class);

  @Test
  void mapsConversationToDetailResponse() {
    Conversation conversation = Conversation.create("customer-001");
    conversation.addCustomerMessage("你好");
    conversation.addAgentReply("您好");

    ConversationDetailResponse response = mapper.toDetailResponse(conversation);

    assertThat(response.getConversationNo()).isEqualTo(conversation.getConversationNo());
    assertThat(response.getCustomerId()).isEqualTo("customer-001");
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(response.getStartedAt()).isNotNull();
    assertThat(response.getMessages()).hasSize(2);
    assertThat(response.getMessages().get(0).getSeqNo()).isEqualTo(1);
    assertThat(response.getMessages().get(0).getRole()).isEqualTo("CUSTOMER");
    assertThat(response.getMessages().get(0).getContent()).isEqualTo("你好");
    assertThat(response.getMessages().get(1).getSeqNo()).isEqualTo(2);
    assertThat(response.getMessages().get(1).getRole()).isEqualTo("AGENT");
    assertThat(response.getFeedback()).isNull();
  }

  @Test
  void mapsEndedConversationWithFeedback() {
    Conversation conversation = Conversation.create("customer-001");
    conversation.addCustomerMessage("你好");
    conversation.addAgentReply("您好");
    conversation.end();
    conversation.submitFeedback(FeedbackRating.SATISFIED, "回复很快");

    ConversationDetailResponse response = mapper.toDetailResponse(conversation);

    assertThat(response.getStatus()).isEqualTo("ENDED");
    assertThat(response.getEndedAt()).isNotNull();
    assertThat(response.getFeedback()).isNotNull();
    assertThat(response.getFeedback().getRating()).isEqualTo("SATISFIED");
    assertThat(response.getFeedback().getComment()).isEqualTo("回复很快");
    assertThat(response.getFeedback().getSubmittedAt()).isNotNull();
  }

  @Test
  void mapsConversationToSummaryResponse() {
    Conversation conversation = Conversation.create("customer-001");

    ConversationSummaryResponse response = mapper.toSummaryResponse(conversation);

    assertThat(response.getConversationNo()).isEqualTo(conversation.getConversationNo());
    assertThat(response.getCustomerId()).isEqualTo("customer-001");
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(response.getStartedAt()).isNotNull();
    assertThat(mapper.toSummaryResponseList(List.of(conversation))).containsExactly(response);
  }

  @Test
  void mapsMessageThread() {
    Conversation conversation = Conversation.create("customer-001");
    conversation.addCustomerMessage("你好");
    conversation.addAgentReply("您好");
    MessageThread thread =
        new MessageThread(
            conversation.getConversationNo(), List.copyOf(conversation.getMessages()));

    MessageThreadResponse response = mapper.toThreadResponse(thread);

    assertThat(response.getConversationNo()).isEqualTo(conversation.getConversationNo());
    assertThat(response.getMessages()).hasSize(2);
    assertThat(response.getMessages().get(0).getContent()).isEqualTo("你好");
    assertThat(response.getMessages().get(1).getContent()).isEqualTo("您好");
  }

  @Test
  void mapsQuickQuestionItem() {
    QuickQuestionItem item = new QuickQuestionItem(1L, 5, "如何联系人工客服？");

    QuickQuestionResponse response = mapper.toQuickQuestionResponse(item);

    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getSortOrder()).isEqualTo(5);
    assertThat(response.getQuestionText()).isEqualTo("如何联系人工客服？");
    assertThat(mapper.toQuickQuestionResponseList(List.of(item))).containsExactly(response);
  }
}
