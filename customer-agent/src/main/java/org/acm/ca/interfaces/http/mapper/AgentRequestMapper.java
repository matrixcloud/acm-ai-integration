package org.acm.ca.interfaces.http.mapper;

import org.acm.ca.application.port.in.GenerateReplyCommand;
import org.acm.ca.interfaces.http.request.AgentReplyRequest;
import org.mapstruct.Mapper;

/** Maps the inbound HTTP DTO to the application command. Nested records share field names. */
@Mapper(componentModel = "spring")
public interface AgentRequestMapper {

  GenerateReplyCommand toCommand(AgentReplyRequest request);
}
