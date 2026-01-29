package com.magicbus.careercatalyst.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class BlobStorageService {

    private static final Logger logger = LoggerFactory.getLogger(BlobStorageService.class);
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);

    private final BlobContainerClient containerClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BlobStorageService(
            @Value("${azure.storage.connection-string:}") String connectionString,
            @Value("${azure.storage.account:}") String accountName,
            @Value("${azure.storage.key:}") String accountKey,
            @Value("${azure.storage.container:}") String containerName) {
        this.containerClient = buildContainerClient(connectionString, accountName, accountKey, containerName);
    }

    public String uploadJson(String blobName, Map<String, Object> payload) {
        if (containerClient == null) {
            logger.warn("Blob storage is not configured. Skipping upload.");
            return "Blob storage is not configured.";
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            BlobClient blob = containerClient.getBlobClient(blobName);
            blob.upload(new ByteArrayInputStream(bytes), bytes.length, true);
            return "";
        } catch (Exception e) {
            logger.warn("Blob upload failed: {}", e.getMessage());
            return e.getMessage();
        }
    }

    public String buildOnboardingBlobName(String studentId, String candidateName) {
        String datePath = DATE_PATH.format(Instant.now());
        String safeStudentId = (studentId == null || studentId.isBlank()) ? "unknown" : studentId.trim();
        String safeCandidate = slugify(candidateName);
        return String.format("onboarding/%s/student-%s-%s-%d.json", datePath, safeStudentId, safeCandidate, System.currentTimeMillis());
    }

    private String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String trimmed = value.trim().toLowerCase();
        String slug = trimmed.replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "unknown" : slug;
    }

    private BlobContainerClient buildContainerClient(String connectionString, String accountName, String accountKey, String containerName) {
        if (containerName == null || containerName.isBlank()) {
            logger.warn("AZURE_STORAGE_CONTAINER not set.");
            return null;
        }
        try {
            BlobServiceClient client;
            if (connectionString != null && !connectionString.isBlank()) {
                client = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
            } else if (accountName != null && !accountName.isBlank() && accountKey != null && !accountKey.isBlank()) {
                String endpoint = "https://" + accountName + ".blob.core.windows.net";
                StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, accountKey);
                client = new BlobServiceClientBuilder().endpoint(endpoint).credential(credential).buildClient();
            } else {
                logger.warn("Azure storage credentials not provided.");
                return null;
            }

            BlobContainerClient container = client.getBlobContainerClient(containerName);
            if (!container.exists()) {
                container.create();
            }
            return container;
        } catch (Exception e) {
            logger.warn("Failed to initialize Blob container: {}", e.getMessage());
            return null;
        }
    }
}
