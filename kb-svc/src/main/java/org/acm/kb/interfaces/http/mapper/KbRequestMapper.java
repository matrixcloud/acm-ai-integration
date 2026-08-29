package org.acm.kb.interfaces.http.mapper;

import org.acm.kb.application.port.in.command.SearchCommand;
import org.acm.kb.interfaces.http.request.SearchRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface KbRequestMapper {

  @Mapping(target = "kbNo", source = "kbNo")
  @Mapping(target = "query", source = "request.query")
  @Mapping(target = "topK", source = "request.topK")
  SearchCommand toSearchCommand(String kbNo, SearchRequest request);
}
