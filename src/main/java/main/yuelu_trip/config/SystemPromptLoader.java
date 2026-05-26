package main.yuelu_trip.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SystemPromptLoader {

    private final String prompt;

    public SystemPromptLoader() {
        try {
            var resource = new ClassPathResource("prompts/system-prompt.txt");
            prompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load system-prompt.txt", e);
        }
    }

    public String getPrompt() {
        return prompt;
    }
}
