package main.yuelu_trip;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import main.yuelu_trip.service.ConversationService;
import main.yuelu_trip.service.DeepSeekService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConversationServiceTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private DeepSeekService deepSeekService;

    @Test
    void multiTurnConversation_shouldRememberContext() {
        ChatMemory memory = conversationService.getOrCreate("test-multi-turn");
        memory.add(new UserMessage("岳麓山有哪些必看的景点？"));

        String reply1 = deepSeekService.chat(memory.messages());
        System.out.println("第一轮AI: " + reply1);
        assertNotNull(reply1);

        memory.add(new AiMessage(reply1));
        memory.add(new UserMessage("帮我规划一条游览路线"));

        String reply2 = deepSeekService.chat(memory.messages());
        System.out.println("第二轮AI: " + reply2);
        assertNotNull(reply2);

        boolean mentionsHistory = reply2.contains("岳麓书院") || reply2.contains("爱晚亭");
        assertTrue(mentionsHistory, "AI应该基于前文提到的景点来规划路线");
    }
}
