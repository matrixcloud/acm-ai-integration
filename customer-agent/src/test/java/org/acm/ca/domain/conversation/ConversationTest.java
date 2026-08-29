package org.acm.ca.domain.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.acm.ca.domain.shared.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConversationTest {

  @Nested
  @DisplayName("创建会话")
  class Create {

    @Test
    void shouldCreateWithActiveStatusAndConversationNo() {
      Conversation conversation = Conversation.create("customer-001");

      assertThat(conversation.getCustomerId()).isEqualTo("customer-001");
      assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
      assertThat(conversation.getConversationNo()).startsWith("CON");
      assertThat(conversation.getStartedAt()).isNotNull();
      assertThat(conversation.getMessages()).isEmpty();
      assertThat(conversation.getFeedback()).isNull();
    }
  }

  @Nested
  @DisplayName("消息收发")
  class Messages {

    @Test
    void shouldAddCustomerMessageWithIncrementingSeqNo() {
      Conversation conversation = Conversation.create("customer-001");

      Message msg1 = conversation.addCustomerMessage("你好");
      Message msg2 = conversation.addAgentReply("您好，有什么可以帮您？");

      assertThat(msg1.getSeqNo()).isEqualTo(1);
      assertThat(msg1.getRole()).isEqualTo(MessageRole.CUSTOMER);
      assertThat(msg1.getContent()).isEqualTo("你好");
      assertThat(msg2.getSeqNo()).isEqualTo(2);
      assertThat(msg2.getRole()).isEqualTo(MessageRole.AGENT);
      assertThat(conversation.getMessages()).hasSize(2);
    }

    @Test
    void shouldTrimCustomerMessageContent() {
      Conversation conversation = Conversation.create("customer-001");

      Message msg = conversation.addCustomerMessage("  你好  ");

      assertThat(msg.getContent()).isEqualTo("你好");
    }

    @Test
    void shouldRejectBlankCustomerMessage() {
      Conversation conversation = Conversation.create("customer-001");

      assertThatThrownBy(() -> conversation.addCustomerMessage("   "))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("blank");
    }

    @Test
    void shouldRejectNullCustomerMessage() {
      Conversation conversation = Conversation.create("customer-001");

      assertThatThrownBy(() -> conversation.addCustomerMessage(null))
          .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void shouldRejectBlankAgentReply() {
      Conversation conversation = Conversation.create("customer-001");

      assertThatThrownBy(() -> conversation.addAgentReply("  "))
          .isInstanceOf(InvalidRequestException.class);
    }
  }

  @Nested
  @DisplayName("结束会话")
  class End {

    @Test
    void shouldTransitionActiveToAwaitingFeedback() {
      Conversation conversation = Conversation.create("customer-001");

      conversation.end();

      assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.AWAITING_FEEDBACK);
      assertThat(conversation.getEndedAt()).isNotNull();
    }

    @Test
    void shouldRejectEndingNonActiveConversation() {
      Conversation conversation = Conversation.create("customer-001");
      conversation.end();

      assertThatThrownBy(() -> conversation.end())
          .isInstanceOf(ConversationStateConflictException.class);
    }
  }

  @Nested
  @DisplayName("提交评价")
  class Feedback {

    @Test
    void shouldTransitionAwaitingFeedbackToEnded() {
      Conversation conversation = Conversation.create("customer-001");
      conversation.end();

      conversation.submitFeedback(FeedbackRating.SATISFIED, "回复很快");

      assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.ENDED);
      assertThat(conversation.getFeedback().getRating()).isEqualTo(FeedbackRating.SATISFIED);
      assertThat(conversation.getFeedback().getComment()).isEqualTo("回复很快");
      assertThat(conversation.getFeedback().getSubmittedAt()).isNotNull();
    }

    @Test
    void shouldRejectFeedbackOnActiveConversation() {
      Conversation conversation = Conversation.create("customer-001");

      assertThatThrownBy(
              () -> conversation.submitFeedback(FeedbackRating.SATISFIED, null))
          .isInstanceOf(ConversationStateConflictException.class);
    }

    @Test
    void shouldRejectDuplicateFeedback() {
      Conversation conversation = Conversation.create("customer-001");
      conversation.end();
      conversation.submitFeedback(FeedbackRating.SATISFIED, null);

      assertThatThrownBy(
              () -> conversation.submitFeedback(FeedbackRating.DISSATISFIED, null))
          .isInstanceOf(ConversationStateConflictException.class);
    }
  }

  @Nested
  @DisplayName("非 ACTIVE 状态禁止发送消息")
  class NotActive {

    @Test
    void shouldRejectMessageOnAwaitingFeedback() {
      Conversation conversation = Conversation.create("customer-001");
      conversation.end();

      assertThatThrownBy(() -> conversation.addCustomerMessage("你好"))
          .isInstanceOf(ConversationNotActiveException.class);
    }

    @Test
    void shouldRejectMessageOnEnded() {
      Conversation conversation = Conversation.create("customer-001");
      conversation.end();
      conversation.submitFeedback(FeedbackRating.SATISFIED, null);

      assertThatThrownBy(() -> conversation.addCustomerMessage("你好"))
          .isInstanceOf(ConversationNotActiveException.class);
    }

    @Test
    void shouldRejectAgentReplyOnEnded() {
      Conversation conversation = Conversation.create("customer-001");
      conversation.end();
      conversation.submitFeedback(FeedbackRating.SATISFIED, null);

      assertThatThrownBy(() -> conversation.addAgentReply("您好"))
          .isInstanceOf(ConversationNotActiveException.class);
    }
  }
}
