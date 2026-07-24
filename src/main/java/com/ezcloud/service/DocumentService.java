package com.ezcloud.service;

import com.ezcloud.domain.DocumentEntity;
import com.ezcloud.dto.DocumentDtos.DocumentResponse;
import com.ezcloud.exception.NotFoundException;
import com.ezcloud.repository.DocumentRepository;
import com.ezcloud.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.function.Predicate.not;

/**
 * RAG ingestion pipeline: upload -> Tika parse -> token-based chunking ->
 * embedding (local ONNX model) -> PGVector storage. Deletion removes both the
 * vector chunks (by metadata filter) and the metadata row.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    public DocumentService(VectorStore vectorStore,
                           DocumentRepository documentRepository,
                           UserRepository userRepository) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    @CacheEvict(cacheNames = "documents", allEntries = true)
    public DocumentResponse ingest(String username, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("empty_file");
        }
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("user_not_found"));

        var parsed = readWithTika(file);
        var chunks = chunkDocument(parsed);

        var entity = new DocumentEntity(
                sanitizeFilename(file.getOriginalFilename()),
                file.getContentType(),
                file.getSize(),
                chunks.size(),
                user.getId());

        storeChunks(chunks, entity);
        documentRepository.save(entity);

        log.info("Ingested document {} ({} chunks)", entity.getFilename(), chunks.size());
        return toResponse(entity);
    }

    @Cacheable("documents")
    public List<DocumentResponse> list() {
        return documentRepository.findAll().stream().map(DocumentService::toResponse).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "documents", allEntries = true)
    public void delete(UUID id) {
        var entity = documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("document_not_found"));
        vectorStore.delete(new FilterExpressionBuilder().eq("documentId", id.toString()).build());
        documentRepository.delete(entity);
        log.info("Deleted document {} and its vector chunks", entity.getFilename());
    }

    /** Splits parsed pages into token-sized chunks suited to the embedding model. */
    private List<Document> chunkDocument(List<Document> parsed) {
        // TODO(4.1): split the parsed pages into chunks with TokenTextSplitter                    [Module 4]
        throw new UnsupportedOperationException("TODO 4.1 — see WORKSHOP.md Module 4");
    }

    /** Stamps each chunk with its source metadata, then embeds + stores in pgvector. */
    private void storeChunks(List<Document> chunks, DocumentEntity entity) {
        // TODO(4.2): stamp each chunk's metadata ("documentId", "filename"),
        //            then embed + store them with vectorStore.add(chunks)                         [Module 4]
        throw new UnsupportedOperationException("TODO 4.2 — see WORKSHOP.md Module 4");
    }

    private List<Document> readWithTika(MultipartFile file) {
        try {
            var reader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
            var parsed = reader.get();
            if (parsed.isEmpty() || parsed.stream().allMatch(d -> d.getText() == null || d.getText().isBlank())) {
                throw new IllegalArgumentException("unreadable_document");
            }
            return parsed;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }

    private static String sanitizeFilename(String original) {
        // strip any path components a client might send
        return Optional.ofNullable(original)
                .filter(not(String::isBlank))
                .map(name -> name.replace('\\', '/'))
                .map(name -> name.substring(name.lastIndexOf('/') + 1))
                .orElse("unnamed");
    }

    private static DocumentResponse toResponse(DocumentEntity entity) {
        return new DocumentResponse(entity.getId(), entity.getFilename(), entity.getContentType(),
                entity.getSizeBytes(), entity.getChunkCount(), entity.getCreatedAt());
    }
}
