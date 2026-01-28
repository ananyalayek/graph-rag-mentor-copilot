package com.magicbus.careercatalyst.onboarding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class PowerAutomateNotifier {

    private final String webhookUrl;

    public PowerAutomateNotifier(@Value("${powerautomate.webhook.url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public void notifyLowConfidence(String studentId, String docType, double confidence) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        String body = String.format(
                "{\"studentId\":\"%s\",\"docType\":\"%s\",\"confidence\":%.2f}",
                studentId, docType, confidence
        );
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {
            // Keep onboarding flow resilient for demo.
        }
    }
}
