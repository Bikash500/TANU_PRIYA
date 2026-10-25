package com.brightwood.students.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assistant")
public class AiAssistantController {

    private final AiAssistantService assistantService;

    public AiAssistantController(AiAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/status")
    public AssistantStatus status() {
        return new AssistantStatus(assistantService.isConfigured());
    }

    @PostMapping("/chat")
    @ResponseStatus(HttpStatus.OK)
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication
    ) {
        return new ChatResponse(assistantService.reply(authentication.getName(), request.messages()));
    }

    public record AssistantStatus(boolean configured) {
    }

    public record ChatRequest(
            @NotEmpty @Size(max = 12) List<@Valid ChatMessage> messages
    ) {
    }

    public record ChatMessage(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 2000) String content
    ) {
    }

    public record ChatResponse(String reply) {
    }
}
