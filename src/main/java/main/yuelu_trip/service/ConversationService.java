package main.yuelu_trip.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import main.yuelu_trip.config.SystemPromptLoader;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationService {

    private final ConcurrentHashMap<String, ChatMemory> conversations = new ConcurrentHashMap<>();
    private final String systemPrompt;

    public ConversationService(SystemPromptLoader promptLoader) {
        this.systemPrompt = promptLoader.getPrompt();
    }

    public ChatMemory getOrCreate(String conversationId) {
        return conversations.computeIfAbsent(conversationId, id -> {
            ChatMemory memory = MessageWindowChatMemory.builder()
                    .maxMessages(20)
                    .build();
            memory.add(new SystemMessage(systemPrompt));
            return memory;
        });
    }
}
