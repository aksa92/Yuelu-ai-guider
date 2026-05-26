package main.yuelu_trip.controller;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import jakarta.validation.Valid;
import main.yuelu_trip.dto.ChatRequest;
import main.yuelu_trip.dto.ChatResponse;
import main.yuelu_trip.service.ConversationService;
import main.yuelu_trip.service.DeepSeekService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final DeepSeekService deepSeekService;
    private final ConversationService conversationService;

    public ChatController(DeepSeekService deepSeekService, ConversationService conversationService) {
        this.deepSeekService = deepSeekService;
        this.conversationService = conversationService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String conversationId = request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();

        ChatMemory memory = conversationService.getOrCreate(conversationId);
        memory.add(new UserMessage(request.message()));

        String reply = deepSeekService.chatWithTools(memory.messages());

        memory.add(new AiMessage(reply));

        return new ChatResponse(conversationId, reply);
    }

    @PostMapping("/stream")
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        String conversationId = request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();

        ChatMemory memory = conversationService.getOrCreate(conversationId);
        memory.add(new UserMessage(request.message()));

        SseEmitter emitter = new SseEmitter(300_000L);
        StringBuilder collector = new StringBuilder();

        deepSeekService.streamChat(memory.messages(), emitter, collector,
                () -> memory.add(new AiMessage(collector.toString())));

        return emitter;
    }
}
