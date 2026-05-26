package main.yuelu_trip;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import main.yuelu_trip.config.GlobalExceptionHandler;
import main.yuelu_trip.controller.ChatController;
import main.yuelu_trip.service.ConversationService;
import main.yuelu_trip.service.DeepSeekService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@Import(GlobalExceptionHandler.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeepSeekService deepSeekService;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    void shouldReplyWhenMessageIsValid() throws Exception {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        memory.add(new SystemMessage("test"));
        when(conversationService.getOrCreate(any())).thenReturn(memory);
        when(deepSeekService.chat(any())).thenReturn("岳麓山欢迎您");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"岳麓山有什么好玩的\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("岳麓山欢迎您"))
                .andExpect(jsonPath("$.conversationId").isNotEmpty());
    }

    @Test
    void shouldReturn400WhenMessageIsBlank() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenMessageIsMissing() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldPreserveConversationId() throws Exception {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        memory.add(new SystemMessage("test"));
        when(conversationService.getOrCreate(any())).thenReturn(memory);
        when(deepSeekService.chat(any())).thenReturn("回复");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\",\"conversationId\":\"my-id\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("my-id"));
    }
}
