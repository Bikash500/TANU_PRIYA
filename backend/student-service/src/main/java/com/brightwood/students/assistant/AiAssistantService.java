package com.brightwood.students.assistant;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.brightwood.students.assistant.AiAssistantController.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AiAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(AiAssistantService.class);
    private static final int MAX_PROMPT_CHARACTERS = 8_000;
    private static final int MAX_REQUESTS_PER_HOUR = 20;
    private static final String SYSTEM_PROMPT = """
            You are the Pandey Technical School AI learning assistant. Give helpful, clear,
            age-appropriate general answers about learning, study skills, and everyday questions.
            You are not connected to school databases and must not claim to know private school records.
            Do not ask for or encourage sharing a student's name, contact details, grades, attendance,
            health information, family information, or other personally identifiable information.
            If a user includes private information, do not repeat it; remind them to remove it.
            Do not make high-stakes decisions about a student's education, health, or safety.
            """;

    private final RestClient restClient;
    private final String model;
    private final String apiKey;
    private final boolean localProvider;
    private final ConcurrentHashMap<String, RequestWindow> requestWindows = new ConcurrentHashMap<>();

    public AiAssistantService(
            @Value("${school.ai.openai.api-key:}") String apiKey,
            @Value("${school.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${school.ai.openai.model:gpt-4o-mini}") String model,
            @Value("${school.ai.provider:openai}") String provider
    ) {
        this.apiKey = apiKey.trim();
        this.model = model.isBlank() ? "gpt-4o-mini" : model.trim();
        String normalizedProvider = provider.trim().toLowerCase(Locale.ROOT);
        if (!normalizedProvider.equals("openai") && !normalizedProvider.equals("ollama")) {
            throw new IllegalArgumentException("AI provider must be either 'openai' or 'ollama'.");
        }
        this.localProvider = normalizedProvider.equals("ollama");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        RestClient.Builder clientBuilder = RestClient.builder()
                .baseUrl(baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim())
                .requestFactory(requestFactory);
        if (!localProvider && !this.apiKey.isBlank()) {
            clientBuilder.defaultHeader("Authorization", "Bearer " + this.apiKey);
        }
        this.restClient = clientBuilder.build();
    }

    public boolean isConfigured() {
        return localProvider || !apiKey.isBlank();
    }

    public String reply(String username, List<ChatMessage> messages) {
        if (!isConfigured()) {
            throw new AiAssistantException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI assistant is not enabled yet. Ask your school administrator to configure it.");
        }

        int promptLength = messages.stream().mapToInt(message -> message.content().length()).sum();
        if (promptLength > MAX_PROMPT_CHARACTERS) {
            throw new AiAssistantException(HttpStatus.BAD_REQUEST,
                    "This conversation is too long. Start a new chat and try again.");
        }

        enforceRateLimit(username);
        List<OpenAiMessage> prompt = new ArrayList<>(messages.size() + 1);
        prompt.add(new OpenAiMessage("system", SYSTEM_PROMPT));
        messages.forEach(message -> prompt.add(new OpenAiMessage(message.role(), message.content())));

        OpenAiResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OpenAiRequest(model, prompt, 700, 0.4))
                    .retrieve()
                    .body(OpenAiResponse.class);
        } catch (RestClientResponseException exception) {
            logger.warn("AI provider returned HTTP {}.", exception.getStatusCode().value());
            throw new AiAssistantException(HttpStatus.BAD_GATEWAY,
                    "The AI service could not process this question. Please try again.");
        } catch (ResourceAccessException exception) {
            logger.warn("AI provider request failed with {}.", exception.getClass().getSimpleName());
            throw new AiAssistantException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI service could not be reached. Please try again shortly.");
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null
                || response.choices().get(0).message().content().isBlank()) {
            throw new AiAssistantException(HttpStatus.BAD_GATEWAY,
                    "The AI service returned an empty answer. Please try again.");
        }
        return response.choices().get(0).message().content().trim();
    }

    private void enforceRateLimit(String username) {
        long currentWindow = Instant.now().getEpochSecond() / Duration.ofHours(1).getSeconds();
        RequestWindow window = requestWindows.compute(username, (key, current) ->
                current == null || current.hour != currentWindow
                        ? new RequestWindow(currentWindow)
                        : current);
        synchronized (window) {
            if (window.count >= MAX_REQUESTS_PER_HOUR) {
                throw new AiAssistantException(HttpStatus.TOO_MANY_REQUESTS,
                        "You have reached the hourly AI chat limit. Please try again later.");
            }
            window.count++;
        }
    }

    private record OpenAiRequest(
            String model,
            List<OpenAiMessage> messages,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature
    ) {
    }

    private record OpenAiMessage(String role, String content) {
    }

    private record OpenAiResponse(List<Choice> choices) {
    }

    private record Choice(OpenAiMessage message) {
    }

    private static final class RequestWindow {
        private final long hour;
        private int count;

        private RequestWindow(long hour) {
            this.hour = hour;
        }
    }
}
