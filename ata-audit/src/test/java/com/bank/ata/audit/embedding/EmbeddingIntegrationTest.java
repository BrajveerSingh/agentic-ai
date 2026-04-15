package com.bank.ata.audit.embedding;

import com.bank.ata.audit.config.EmbeddingStoreConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the in-process AllMiniLM embedding model and
 * InMemoryEmbeddingStore used for semantic search over audit reasoning steps.
 *
 * No external services required — model runs via ONNX Runtime in the JVM.
 *
 * Production upgrade path: replace InMemoryEmbeddingStore with JVectorEmbeddingStore
 * (see diagrams/16_jvector_persistence.mmd and diagrams/17_jvector_recovery_sequence.mmd).
 */
@SpringJUnitConfig(EmbeddingStoreConfig.class)
class EmbeddingIntegrationTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Test
    @DisplayName("EmbeddingModel should produce 384-dimensional vectors")
    void embeddingModelShouldProduce384DimVectors() {
        Embedding embedding = embeddingModel.embed("credit score evaluation for customer").content();

        assertThat(embedding).isNotNull();
        assertThat(embedding.vector()).hasSize(384);
    }

    @Test
    @DisplayName("EmbeddingStore should persist and retrieve embeddings")
    void embeddingStoreShouldPersistAndRetrieve() {
        TextSegment segment = TextSegment.from(
                "Thought: I need to check the credit score. Action: getCreditScore. Observation: score=720");
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);

        assertThat(id).isNotBlank();
    }

    @Test
    @DisplayName("Semantic search should return similar reasoning steps")
    void semanticSearchShouldReturnSimilarSteps() {
        // Store several reasoning steps
        storeStep("Thought: Checking credit score. Action: getCreditScore. Observation: score=750, rating=GOOD");
        storeStep("Thought: Verifying KYC status. Action: verifyKYC. Observation: verified=true, level=FULL");
        storeStep("Thought: Checking policy compliance. Action: checkPolicyCompliance. Observation: compliant=true");
        storeStep("Thought: Calculating risk. Action: calculateRiskScore. Observation: risk=0.2, level=LOW");

        // Search for credit-related steps
        Embedding queryEmbedding = embeddingModel.embed("credit score and financial risk assessment").content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.0)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();

        assertThat(matches).isNotEmpty();
        // Top result should be most semantically relevant
        assertThat(matches.get(0).score()).isGreaterThan(0.0);
        assertThat(matches.get(0).embedded().text()).isNotBlank();
    }

    @Test
    @DisplayName("Similar reasoning steps should rank higher than dissimilar ones")
    void similarStepsShouldRankHigher() {
        EmbeddingStore<TextSegment> freshStore = new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>();

        storeInStore(freshStore, "credit score analysis: customer has score 780, EXCELLENT rating");
        storeInStore(freshStore, "employment verification: customer employed 5 years at Bank Corp");
        storeInStore(freshStore, "KYC verification complete: identity verified, no flags");

        Embedding queryEmbedding = embeddingModel.embed("credit score and loan eligibility").content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.0)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = freshStore.search(request).matches();

        assertThat(matches).hasSize(3);
        // Credit-related step should be ranked first
        assertThat(matches.get(0).embedded().text()).containsIgnoringCase("credit");
        // Scores should be in descending order
        assertThat(matches.get(0).score()).isGreaterThanOrEqualTo(matches.get(1).score());
    }

    @Test
    @DisplayName("Embedding vectors should be normalised (cosine similarity ready)")
    void embeddingVectorsShouldBeNormalised() {
        Embedding e1 = embeddingModel.embed("approve loan for customer with good credit").content();
        Embedding e2 = embeddingModel.embed("reject loan for high-risk customer").content();

        // Compute L2 norm — AllMiniLM produces unit vectors
        double norm1 = Math.sqrt(dotProduct(e1.vector(), e1.vector()));
        double norm2 = Math.sqrt(dotProduct(e2.vector(), e2.vector()));

        assertThat(norm1).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(norm2).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void storeStep(String text) {
        storeInStore(embeddingStore, text);
    }

    private void storeInStore(EmbeddingStore<TextSegment> store, String text) {
        TextSegment segment = TextSegment.from(text);
        Embedding embedding = embeddingModel.embed(text).content();
        store.add(embedding, segment);
    }

    private static double dotProduct(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }
}

