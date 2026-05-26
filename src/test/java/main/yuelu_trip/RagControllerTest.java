package main.yuelu_trip;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import main.yuelu_trip.config.GlobalExceptionHandler;
import main.yuelu_trip.controller.RagController;
import main.yuelu_trip.service.ConversationService;
import main.yuelu_trip.service.RagEvaluator;
import main.yuelu_trip.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagController.class)
@Import(GlobalExceptionHandler.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagService ragService;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private RagEvaluator evaluator;

    @Test
    void shouldReplyWhenMessageIsValid() throws Exception {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        memory.add(new SystemMessage("test"));
        when(conversationService.getOrCreate(any())).thenReturn(memory);
        when(ragService.chat(any(), any(), any())).thenReturn("岳麓山欢迎您");

        mockMvc.perform(post("/api/rag/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"岳麓山有什么好玩的\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("岳麓山欢迎您"))
                .andExpect(jsonPath("$.conversationId").isNotEmpty());
    }

    @Test
    void shouldReturn400WhenMessageIsBlank() throws Exception {
        mockMvc.perform(post("/api/rag/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenMessageIsMissing() throws Exception {
        mockMvc.perform(post("/api/rag/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnEvalReport() throws Exception {
        when(evaluator.generateReport()).thenReturn("测试报告");

        mockMvc.perform(get("/api/rag/eval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report").value("测试报告"));
    }
}
