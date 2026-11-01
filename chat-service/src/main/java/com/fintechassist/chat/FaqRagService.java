package com.fintechassist.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FaqRagService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FaqRagService.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate vectorJdbc;
    private final RestClient banking;
    private final int topK;
    private volatile boolean ingested = false;

    public FaqRagService(VectorStore vectorStore,
                         @Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbc,
                         @Value("${banking.url}") String bankingUrl,
                         @Value("${chat.rag-top-k}") int topK) {
        this.vectorStore = vectorStore;
        this.vectorJdbc = vectorJdbc;
        this.banking = RestClient.builder().baseUrl(bankingUrl).build();
        this.topK = topK;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIngested();
    }

    public synchronized void ensureIngested() {
        if (ingested) return;
        try {
            Integer count = vectorJdbc.queryForObject("select count(*) from vector_store", Integer.class);
            if (count == null || count == 0) {
                List<Map<String, Object>> faqs = banking.get().uri("/api/faqs").retrieve()
                        .body(new ParameterizedTypeReference<>() {});
                List<Document> docs = faqs.stream().map(f -> new Document(
                        UUID.nameUUIDFromBytes(String.valueOf(f.get("id")).getBytes(StandardCharsets.UTF_8)).toString(),
                        "Q: " + f.get("question") + "\nA: " + f.get("answer"),
                        Map.of("category", String.valueOf(f.get("category"))))).toList();
                vectorStore.add(docs);
                log.info("Ingested {} FAQs into the vector store", docs.size());
            }
            ingested = true;
        } catch (Exception e) {
            log.warn("FAQ ingestion skipped (is banking-service running?): {}", e.getMessage());
        }
    }

    public List<String> search(String query) {
        ensureIngested();
        try {
            return vectorStore.similaritySearch(SearchRequest.builder()
                            .query(query).topK(topK).similarityThreshold(0.25).build())
                    .stream().map(Document::getText).toList();
        } catch (Exception e) {
            log.warn("FAQ search failed: {}", e.getMessage());
            return List.of();
        }
    }
}
