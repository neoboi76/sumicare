/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GeminiNarrativeService {

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String PROMPT_PREFIX =
            "You are a professional financial analyst for a spa business. Based on the following report data, "
            + "write a concise management interpretation of no more than 280 words. Use formal English. "
            + "Discuss performance trends, identify notable strengths or weaknesses, and provide at least one "
            + "actionable recommendation. Do not use bullet points or headers — write in continuous prose paragraphs. "
            + "Do not mention that you are an AI. Report data:\n\n";

    private final RestClient restClient;
    private final String model;
    private final boolean enabled;

    public GeminiNarrativeService(
            @Value("${sumicare.gemini.apiKey:}") String apiKey,
            @Value("${sumicare.gemini.model:gemini-2.5-pro}") String model) {
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = this.enabled
                ? RestClient.builder()
                    .baseUrl(GEMINI_BASE + model + ":generateContent?key=" + apiKey)
                    .build()
                : RestClient.builder().build();
    }

    public String generateInterpretation(String reportContext) {
        if (!enabled || reportContext == null || reportContext.isBlank()) {
            return templateFallback(reportContext);
        }
        try {
            String prompt = PROMPT_PREFIX + reportContext;
            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
                )
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            return extractText(response);
        } catch (Exception e) {
            return templateFallback(reportContext);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        if (response == null) return null;
        Object candidates = response.get("candidates");
        if (!(candidates instanceof List<?> list) || list.isEmpty()) return null;
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> candidate)) return null;
        Object content = candidate.get("content");
        if (!(content instanceof Map<?, ?> contentMap)) return null;
        Object parts = contentMap.get("parts");
        if (!(parts instanceof List<?> partsList) || partsList.isEmpty()) return null;
        Object part = partsList.get(0);
        if (!(part instanceof Map<?, ?> partMap)) return null;
        Object text = partMap.get("text");
        return text instanceof String s ? s.trim() : null;
    }

    private String templateFallback(String reportContext) {
        if (reportContext == null || reportContext.isBlank()) {
            return "The selected period produced no reportable data. Review the date range or confirm that "
                    + "transactions have been recorded for this period before drawing conclusions.";
        }
        return "The data presented in this report reflects operations for the selected period. Management is "
                + "encouraged to review the figures against the prior period to identify meaningful trends. "
                + "Areas where performance falls below the running average warrant closer operational review, "
                + "while areas of strength should be examined for practices that can be replicated across the "
                + "organisation. Where revenue concentration is observed in specific services or therapists, "
                + "contingency planning is advised to mitigate dependency risk.";
    }
}
