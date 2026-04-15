package com.bank.ata.audit.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the embedding model and store used for semantic search over audit
 * reasoning steps.
 *
 * <p><b>Current implementation:</b> AllMiniLM-L6-v2 (quantized ONNX, 384-dim)
 * runs fully in-process — no external service required. Embeddings are stored in
 * {@link InMemoryEmbeddingStore} which resets on restart.</p>
 *
 * <p><b>Production upgrade path:</b> Replace {@link InMemoryEmbeddingStore} with
 * {@code JVectorEmbeddingStore} (see {@code diagrams/16_jvector_persistence.mmd})
 * for durable HNSW-graph persistence on disk with PostgreSQL WAL-based recovery.</p>
 */
@Configuration
public class EmbeddingStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStoreConfig.class);

    /**
     * AllMiniLM-L6-v2 quantized embedding model.
     * Runs entirely in-process via ONNX Runtime — no Ollama or GPU required.
     * Produces 384-dimensional embeddings.
     */
    @Bean
    public EmbeddingModel auditEmbeddingModel() {
        log.info("Initializing AllMiniLmL6V2QuantizedEmbeddingModel for audit reasoning-step embeddings");
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    /**
     * In-memory embedding store for reasoning step semantic search.
     *
     * TODO (Phase 2 P1): Replace with JVectorEmbeddingStore for persistence:
     *   JVectorEmbeddingStore.builder()
     *       .directory(Paths.get("./jvector-audit"))
     *       .dimension(384)
     *       .build();
     */
    @Bean
    public EmbeddingStore<TextSegment> auditEmbeddingStore() {
        log.info("Initializing InMemoryEmbeddingStore for audit embeddings (upgrade to JVector for production)");
        return new InMemoryEmbeddingStore<>();
    }
}

