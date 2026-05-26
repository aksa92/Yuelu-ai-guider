package main.yuelu_trip;

import dev.langchain4j.data.message.UserMessage;
import main.yuelu_trip.service.DeepSeekService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class DeepSeekServiceTest {

    @Autowired
    private DeepSeekService deepSeekService;

    @Test
    void shouldReplyToChineseQuestion() {
        String reply = deepSeekService.chat(List.of(new UserMessage("用一句话介绍岳麓山")));
        System.out.println("AI回复: " + reply);
        assertNotNull(reply);
        assertFalse(reply.isBlank());
    }

    @Test
    void shouldReplyToEnglishQuestion() {
        String reply = deepSeekService.chat(List.of(new UserMessage("Introduce Yuelu Mountain in one sentence")));
        System.out.println("AI回复: " + reply);
        assertNotNull(reply);
        assertFalse(reply.isBlank());
    }
}
