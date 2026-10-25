package com.brightwood.students.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiAssistantServiceTest {

    @Test
    void ollamaProviderDoesNotRequireAnApiKey() {
        AiAssistantService service = new AiAssistantService(
                "",
                "http://127.0.0.1:11434/v1",
                "qwen2.5:3b",
                "ollama"
        );

        assertThat(service.isConfigured()).isTrue();
    }

    @Test
    void openAiProviderRequiresAnApiKey() {
        AiAssistantService service = new AiAssistantService(
                "",
                "https://api.openai.com/v1",
                "gpt-4o-mini",
                "openai"
        );

        assertThat(service.isConfigured()).isFalse();
    }
}
