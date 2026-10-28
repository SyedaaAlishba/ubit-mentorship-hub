package com.ubit.mentorshiphub.rag.repository;

import com.ubit.mentorshiphub.rag.model.RagKnowledgeChunk;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RagKnowledgeChunkRepository extends MongoRepository<RagKnowledgeChunk, ObjectId> {
    Optional<RagKnowledgeChunk> findByChunkId(String chunkId);
    List<RagKnowledgeChunk> findBySourceDocument(String sourceDocument);
    void deleteBySourceDocument(String sourceDocument);
}
