package com.sumicare.auth.email;

import com.sumicare.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "sumicare.email.provider", havingValue = "brevo")
public class BrevoEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);
    private static final String ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private final AppProperties appProperties;
    private final RestClient restClient;

    public BrevoEmailSender(AppProperties appProperties, RestClient.Builder restClientBuilder) {
        this.appProperties = appProperties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public void send(EmailMessage message) {
        String apiKey = appProperties.email().brevoApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("BREVO_API_KEY is not configured");
        }

        Map<String, Object> sender = new HashMap<>();
        sender.put("email", appProperties.app().emailFrom());
        String fromName = appProperties.email().fromName();
        if (fromName != null && !fromName.isBlank()) {
            sender.put("name", fromName);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("sender", sender);
        body.put("to", List.of(Map.of("email", message.to())));
        body.put("subject", message.subject());
        body.put("htmlContent", message.html());

        List<Map<String, String>> attachments = new ArrayList<>();
        for (InlineImage image : message.inlineImages()) {
            attachments.add(Map.of(
                    "name", image.filename(),
                    "content", Base64.getEncoder().encodeToString(image.png())));
        }
        for (Attachment attachment : message.attachments()) {
            attachments.add(Map.of(
                    "name", attachment.filename(),
                    "content", Base64.getEncoder().encodeToString(attachment.content())));
        }
        if (!attachments.isEmpty()) {
            body.put("attachment", attachments);
        }

        try {
            restClient.post()
                    .uri(ENDPOINT)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("Brevo rejected the request: status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to send email via Brevo: " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email via Brevo", e);
        }
    }
}
