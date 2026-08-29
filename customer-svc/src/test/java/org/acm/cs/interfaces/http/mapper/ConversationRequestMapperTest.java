package org.acm.cs.interfaces.http.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.acm.cs.application.port.in.command.CreateConversationCommand;
import org.acm.cs.application.port.in.command.SubmitFeedbackCommand;
import org.acm.cs.application.port.in.query.SearchConversationQuery;
import org.acm.cs.domain.conversation.ConversationStatus;
import org.acm.cs.domain.conversation.FeedbackRating;
import org.acm.cs.domain.shared.InvalidRequestException;
import org.acm.cs.interfaces.http.request.CreateConversationRequest;
import org.acm.cs.interfaces.http.request.SearchConversationRequest;
import org.acm.cs.interfaces.http.request.SubmitFeedbackRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ConversationRequestMapperTest {

  private final ConversationRequestMapper mapper =
      Mappers.getMapper(ConversationRequestMapper.class);

  @Test
  void mapsCreateRequest() {
    CreateConversationRequest request = new CreateConversationRequest();
    request.setCustomerId("customer-001");

    CreateConversationCommand command = mapper.toCommand(request);

    assertThat(command.getCustomerId()).isEqualTo("customer-001");
  }

  @Test
  void mapsSearchRequestAndParsesStatus() {
    SearchConversationRequest request = new SearchConversationRequest();
    request.setCustomerId("customer-001");
    request.setStatus("ACTIVE");
    request.setPage(2);
    request.setSize(10);

    SearchConversationQuery query = mapper.toQuery(request);

    assertThat(query.getCustomerId()).isEqualTo("customer-001");
    assertThat(query.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
    assertThat(query.getPage()).isEqualTo(2);
    assertThat(query.getSize()).isEqualTo(10);
  }

  @Test
  void parseStatusTreatsMissingValuesAsNoFilter() {
    assertThat(mapper.parseStatus(null)).isNull();
    assertThat(mapper.parseStatus(" ")).isNull();
  }

  @Test
  void parseStatusRejectsUnsupportedValue() {
    assertThatThrownBy(() -> mapper.parseStatus("UNKNOWN"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unsupported conversation status 'UNKNOWN'");
  }

  @Test
  void mapsSubmitFeedbackCommand() {
    SubmitFeedbackRequest request = new SubmitFeedbackRequest();
    request.setRating(FeedbackRating.SATISFIED);
    request.setComment("回复很快");

    SubmitFeedbackCommand command = mapper.toCommand("CON-1", request);

    assertThat(command.getConversationNo()).isEqualTo("CON-1");
    assertThat(command.getRating()).isEqualTo(FeedbackRating.SATISFIED);
    assertThat(command.getComment()).isEqualTo("回复很快");
  }
}
