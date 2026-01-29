package com.magicbus.careercatalyst.databricks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class DatabricksVectorSearchClient {

    private static final Logger logger = LoggerFactory.getLogger(DatabricksVectorSearchClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String workspaceUrl;
    private final String pat;
    private final String indexName;
    private final String textColumn;
    private final int defaultNumResults;

    public DatabricksVectorSearchClient(
            @Value("${databricks.workspace-url:}") String workspaceUrl,
            @Value("${databricks.pat:}") String pat,
            @Value("${databricks.vector-search.index:}") String indexName,
            @Value("${databricks.vector-search.text-column:text}") String textColumn,
            @Value("${databricks.vector-search.num-results:3}") int defaultNumResults) {
        this.workspaceUrl = trimTrailingSlash(workspaceUrl);
        this.pat = pat;
        this.indexName = indexName;
        this.textColumn = textColumn;
        this.defaultNumResults = defaultNumResults;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.objectMapper = new ObjectMapper();
    }

    public List<Document> similaritySearch(String queryText, int topK) {
        if (isBlank(workspaceUrl) || isBlank(pat) || isBlank(indexName)) {
            logger.warn("Databricks Vector Search not configured. Returning empty RAG context.");
            return Collections.emptyList();
        }

        int k = topK > 0 ? topK : defaultNumResults;
        String encodedIndex = URLEncoder.encode(indexName, StandardCharsets.UTF_8);
        URI uri = URI.create(workspaceUrl + "/api/2.0/vector-search/indexes/" + encodedIndex + "/query");

        try {
            Map<String, Object> body = Map.of(
                    "query_text", queryText,
                    "num_results", k,
                    "columns", List.of(textColumn)
            );
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + pat)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                logger.warn("Databricks Vector Search query failed with status {}", response.statusCode());
                return Collections.emptyList();
            }

            return parseDocuments(response.body());
        } catch (Exception e) {
            logger.warn("Databricks Vector Search query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Document> parseDocuments(String json) {
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Object resultObj = root.getOrDefault("result", root);
            if (!(resultObj instanceof Map<?, ?> result)) {
                return Collections.emptyList();
            }

            Object dataArrayObj = result.get("data_array");
            if (!(dataArrayObj instanceof List<?> dataArray)) {
                return Collections.emptyList();
            }

            List<Document> docs = new ArrayList<>();
            for (Object rowObj : dataArray) {
                if (rowObj instanceof List<?> row && !row.isEmpty()) {
                    Object textObj = row.get(0);
                    if (textObj != null) {
                        docs.add(new Document(textObj.toString()));
                    }
                }
            }
            return docs;
        } catch (Exception e) {
            logger.warn("Failed to parse Vector Search response: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
