package com.magicbus.careercatalyst.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class AzureDocumentIntelligenceClient {

    private static final Logger logger = LoggerFactory.getLogger(AzureDocumentIntelligenceClient.class);
    private final String endpoint;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AzureDocumentIntelligenceClient(
            @Value("${azure.docintelligence.endpoint:}") String endpoint,
            @Value("${azure.docintelligence.key:}") String apiKey) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    public DocumentExtractionResult analyzeIdDocument(byte[] bytes, String modelId) {
        String resolved = resolveIdModel(modelId);
        return analyzeDocument(bytes, resolved, false);
    }

    public DocumentExtractionResult analyzeIncomeDocument(byte[] bytes) {
        return analyzeDocument(bytes, "prebuilt-document", true);
    }

    private DocumentExtractionResult analyzeDocument(byte[] bytes, String modelId, boolean isIncome) {
        if (endpoint == null || endpoint.isBlank() || apiKey == null || apiKey.isBlank()) {
            logger.warn("Azure Document Intelligence not configured. Check AZURE_DOCINTEL_ENDPOINT and AZURE_DOCINTEL_KEY.");
            return new DocumentExtractionResult("", "", "", "", 0.0);
        }
        try {
            JsonNode root = analyzeWithFallback(bytes, modelId);
            if (root == null) {
                return new DocumentExtractionResult("", "", "", "", 0.0);
            }

            JsonNode documents = root.path("documents");
            if (!documents.isArray() || documents.isEmpty()) {
                logger.warn("Document Intelligence returned no documents. body={}", trimBody(root.toString()));
                return new DocumentExtractionResult("", "", "", "", 0.0);
            }

            JsonNode fields = documents.get(0).path("fields");
            String name = getFieldValue(fields, "FullName", "content");
            if (name.isBlank()) {
                String first = getFieldValue(fields, "FirstName", "content");
                String last = getFieldValue(fields, "LastName", "content");
                name = (first + " " + last).trim();
            }

            String dob = getFieldValue(fields, "DateOfBirth", "valueDate");
            String idNumber = getFieldValue(fields, "DocumentNumber", "content");
            String income = isIncome ? getFieldValue(fields, "TotalIncome", "content") : "";

            double confidence = getFieldConfidence(fields, "DocumentNumber");
            if (confidence == 0.0) {
                confidence = getFieldConfidence(fields, "FullName");
            }

            return new DocumentExtractionResult(name, dob, idNumber, income, confidence);
        } catch (Exception e) {
            logger.warn("Document Intelligence exception: {}", e.getMessage());
            return new DocumentExtractionResult("", "", "", "", 0.0);
        }
    }

    private JsonNode analyzeWithFallback(byte[] bytes, String modelId) {
        String[] basePaths = new String[] {
                "/documentintelligence/documentModels/",
                "/formrecognizer/documentModels/"
        };

        for (String basePath : basePaths) {
            try {
                String analyzeUrl = endpoint + basePath + modelId + ":analyze?api-version=2023-07-31";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(analyzeUrl))
                        .header("Content-Type", "application/octet-stream")
                        .header("Ocp-Apim-Subscription-Key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    logger.warn("Document Intelligence endpoint not found at {} (404). Trying fallback path.", basePath);
                    continue;
                }
                if (response.statusCode() >= 300 && response.statusCode() != 202) {
                    logger.warn("Document Intelligence analyze failed: status={} body={}", response.statusCode(), trimBody(response.body()));
                    return null;
                }

                if (response.statusCode() == 202) {
                    String operationLocation = response.headers().firstValue("operation-location").orElse("");
                    if (operationLocation.isBlank()) {
                        logger.warn("Document Intelligence returned 202 but no operation-location header.");
                        return null;
                    }
                    JsonNode polled = pollForResult(operationLocation);
                    if (polled == null) {
                        return null;
                    }
                    return polled.path("analyzeResult");
                }

                return objectMapper.readTree(response.body());
            } catch (Exception e) {
                logger.warn("Document Intelligence analyze exception: {}", e.getMessage());
                return null;
            }
        }

        logger.warn("Document Intelligence endpoint not found for both documentintelligence and formrecognizer paths.");
        return null;
    }

    private String resolveIdModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return "prebuilt-idDocument";
        }
        String normalized = modelId.trim().toLowerCase();
        if ("aadhaar".equals(normalized) || "pan".equals(normalized)) {
            return "prebuilt-idDocument";
        }
        return modelId;
    }

    private JsonNode pollForResult(String operationLocation) {
        try {
            for (int i = 0; i < 15; i++) {
                HttpRequest pollRequest = HttpRequest.newBuilder()
                        .uri(URI.create(operationLocation))
                        .header("Ocp-Apim-Subscription-Key", apiKey)
                        .GET()
                        .build();

                HttpResponse<String> pollResponse = httpClient.send(pollRequest, HttpResponse.BodyHandlers.ofString());
                if (pollResponse.statusCode() >= 300) {
                    logger.warn("Document Intelligence poll failed: status={} body={}", pollResponse.statusCode(), trimBody(pollResponse.body()));
                    return null;
                }

                JsonNode root = objectMapper.readTree(pollResponse.body());
                String status = root.path("status").asText("");
                if ("succeeded".equalsIgnoreCase(status)) {
                    return root;
                }
                if ("failed".equalsIgnoreCase(status)) {
                    logger.warn("Document Intelligence analysis failed: body={}", trimBody(pollResponse.body()));
                    return null;
                }
                Thread.sleep(1000L);
            }
            logger.warn("Document Intelligence poll timed out.");
            return null;
        } catch (Exception e) {
            logger.warn("Document Intelligence poll exception: {}", e.getMessage());
            return null;
        }
    }

    private String trimBody(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) + "…" : trimmed;
    }

    private String getFieldValue(JsonNode fields, String key, String valueKey) {
        JsonNode node = fields.path(key);
        if (node.isMissingNode()) {
            return "";
        }
        JsonNode valueNode = node.path(valueKey);
        return valueNode.isMissingNode() ? "" : valueNode.asText("");
    }

    private double getFieldConfidence(JsonNode fields, String key) {
        JsonNode node = fields.path(key);
        if (node.isMissingNode()) {
            return 0.0;
        }
        JsonNode valueNode = node.path("confidence");
        return valueNode.isMissingNode() ? 0.0 : valueNode.asDouble(0.0);
    }
}
