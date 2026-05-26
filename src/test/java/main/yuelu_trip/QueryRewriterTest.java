package main.yuelu_trip;

import main.yuelu_trip.service.DeepSeekService;
import main.yuelu_trip.service.QueryRewriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryRewriterTest {

    @Mock
    private DeepSeekService deepSeekService;

    @InjectMocks
    private QueryRewriter queryRewriter;

    @Test
    void shouldReturnMultipleQueries() {
        when(deepSeekService.chat(any())).thenReturn("岳麓山历史\n岳麓山景点\n岳麓山路线");

        List<String> queries = queryRewriter.rewrite("岳麓山有什么好玩的", "test-id");

        assertEquals(3, queries.size());
        assertTrue(queries.get(0).contains("岳麓山"));
    }

    @Test
    void shouldStripNumberPrefixes() {
        when(deepSeekService.chat(any())).thenReturn("1.岳麓山历史\n2.岳麓山景点\n3.岳麓山路线");

        List<String> queries = queryRewriter.rewrite("有什么推荐的", "test-id");

        assertFalse(queries.get(0).startsWith("1"));
    }

    @Test
    void shouldHandleEmptyReply() {
        when(deepSeekService.chat(any())).thenReturn("");

        List<String> queries = queryRewriter.rewrite("岳麓山", "test-id");

        assertTrue(queries.isEmpty());
    }

    @Test
    void shouldUseHistoryForPronounResolution() {
        when(deepSeekService.chat(any())).thenReturn("爱晚亭开放时间\n爱晚亭门票\n爱晚亭历史");

        List<String> firstQueries = queryRewriter.rewrite("爱晚亭什么时候开放", "pronoun-test");

        assertEquals(3, firstQueries.size());
        assertTrue(firstQueries.get(0).contains("爱晚亭"));
    }
}
