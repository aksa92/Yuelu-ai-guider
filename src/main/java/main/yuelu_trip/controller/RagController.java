package main.yuelu_trip.controller;

import dev.langchain4j.memory.ChatMemory;
import jakarta.validation.Valid;
import main.yuelu_trip.dto.ChatRequest;
import main.yuelu_trip.dto.ChatResponse;
import main.yuelu_trip.service.ConversationService;
import main.yuelu_trip.service.RagEvaluator;
import main.yuelu_trip.service.RagService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final ConversationService conversationService;
    private final RagEvaluator evaluator;

    public RagController(RagService ragService, ConversationService conversationService, RagEvaluator evaluator) {
        this.ragService = ragService;
        this.conversationService = conversationService;
        this.evaluator = evaluator;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String conversationId = request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();

        ChatMemory memory = conversationService.getOrCreate(conversationId);

        String reply = ragService.chat(request.message(), conversationId, memory);

        return new ChatResponse(conversationId, reply);
    }

    @GetMapping("/eval")
    public Map<String, String> evalReport() {
        return Map.of("report", evaluator.generateReport());
    }
}
