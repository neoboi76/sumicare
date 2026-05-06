package com.sumicare.recommendation.service;

import com.sumicare.common.config.AppProperties;
import com.sumicare.recommendation.dto.QuizAnswer;
import com.sumicare.service_catalogue.domain.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@org.springframework.stereotype.Service
public class RecommendationExplainerService {

    private static final String DEFAULT_DISCLAIMER =
            "SumiCare's recommendations are for relaxation purposes only and do not constitute medical advice.";

    private final AppProperties appProperties;
    private final RestClient restClient = RestClient.create();

    public RecommendationExplainerService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String disclaimer() {
        return DEFAULT_DISCLAIMER;
    }

    public boolean isAvailable() {
        return appProperties.anthropic() != null
                && appProperties.anthropic().apiKey() != null
                && !appProperties.anthropic().apiKey().isBlank();
    }

    public String generateRationale(Service primary, List<QuizAnswer> answers) {
        if (!isAvailable() || primary == null) return null;
        try {
            String prompt = "A spa client answered a relaxation preference quiz. Their answers were: "
                    + answers.toString()
                    + ". The recommendation engine has selected the service \""
                    + primary.getName()
                    + "\". In two short sentences, explain why this is a relaxing choice for them."
                    + " Avoid medical claims. Frame it strictly for relaxation.";
            Map<String, Object> body = Map.of(
                    "model", "claude-haiku-4-5",
                    "max_tokens", 200,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", appProperties.anthropic().apiKey());
            headers.set("anthropic-version", "2023-06-01");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            Map<?, ?> response = restClient.post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .headers(h -> h.addAll(headers))
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null) return null;
            Object content = response.get("content");
            if (content instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                Object text = first.get("text");
                return text == null ? null : text.toString();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
