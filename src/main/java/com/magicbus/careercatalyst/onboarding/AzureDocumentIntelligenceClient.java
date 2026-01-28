package com.magicbus.careercatalyst.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class AzureDocumentIntelligenceClient {

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
        return analyzeDocument(bytes, modelId, false);
    }

    public DocumentExtractionResult analyzeIncomeDocument(byte[] bytes) {
        return analyzeDocument(bytes, "prebuilt-document", true);
    }

    private DocumentExtractionResult analyzeDocument(byte[] bytes, String modelId, boolean isIncome) {
        if (endpoint == null || endpoint.isBlank() || apiKey == null || apiKey.isBlank()) {
            return new DocumentExtractionResult("", "", "", "", 0.0);
        }
        try {
            String analyzeUrl = endpoint + "/documentintelligence/documentModels/" + modelId + ":analyze?api-version=2023-07-31";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(analyzeUrl))
                    .header("Content-Type", "application/octet-stream")
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return new DocumentExtractionResult("", "", "", "", 0.0);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode documents = root.path("documents");
            if (!documents.isArray() || documents.isEmpty()) {
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
            return new DocumentExtractionResult("", "", "", "", 0.0);
        }
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
