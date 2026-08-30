package org.acm.kb.interfaces.http.mapper;

import java.util.List;
import org.acm.kb.domain.kb.Document;
import org.acm.kb.domain.kb.KbChunk;
import org.acm.kb.domain.kb.KnowledgeBase;
import org.acm.kb.interfaces.http.response.DocumentResponse;
import org.acm.kb.interfaces.http.response.KbDetailResponse;
import org.acm.kb.interfaces.http.response.KbResponse;
import org.acm.kb.interfaces.http.response.SearchResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface KbResponseMapper {

  KbResponse toKbResponse(KnowledgeBase knowledgeBase);

  List<KbResponse> toKbResponseList(List<KnowledgeBase> knowledgeBases);

  DocumentResponse toDocumentResponse(Document document);

  List<DocumentResponse> toDocumentResponseList(List<Document> documents);

  default SearchResponse toSearchResponse(List<KbChunk> chunks) {
    SearchResponse response = new SearchResponse();
    response.setChunks(
        chunks.stream()
            .map(
                chunk -> {
                  SearchResponse.ChunkResponse chunkResponse = new SearchResponse.ChunkResponse();
                  chunkResponse.setContent(chunk.content());
                  chunkResponse.setScore(chunk.score());
                  chunkResponse.setDocumentNo(chunk.documentNo());
                  chunkResponse.setDocumentName(chunk.documentName());
                  return chunkResponse;
                })
            .toList());
    return response;
  }

  default KbDetailResponse toDetailResponse(KnowledgeBase kb, List<Document> documents) {
    KbDetailResponse response = new KbDetailResponse();
    response.setKbNo(kb.getKbNo());
    response.setName(kb.getName());
    response.setStatus(kb.getStatus().name());
    response.setDocCount(kb.getDocCount());
    response.setCreatedAt(kb.getCreatedAt());
    response.setDocuments(toDocumentResponseList(documents));
    return response;
  }
}
