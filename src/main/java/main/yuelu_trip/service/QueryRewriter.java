package main.yuelu_trip.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

    private static final String SYSTEM_PROMPT = """
            你是一个查询改写助手。用户正在和岳麓山导游对话，请根据对话历史，把用户最新的问题改写成适合向量检索的查询词。

            要求：
            - 如果用户问题中有"那里"、"这边"、"它"、"这个"等指代词，结合历史将其还原为具体景点或地点
            - 分析问题是否包含多个子问题（如出现"和"、"以及"、"、"、多个问号等）
              - 如果是复合问题，先拆成子问题，再为每个子问题生成1-2个检索词
              - 如果是单一问题，生成3个不同角度的检索词
            - 每个检索词不超过20字
            - 直接输出检索词，每行一个，不要编号，不要额外说明
            """;

    private final DeepSeekService deepSeekService;
    private final ConcurrentHashMap<String, String> previousQuestions = new ConcurrentHashMap<>();

    public QueryRewriter(DeepSeekService deepSeekService) {
        this.deepSeekService = deepSeekService;
    }

    public List<String> rewrite(String userMessage, String conversationId) {
        String previous = previousQuestions.get(conversationId);
        previousQuestions.put(conversationId, userMessage);

        String history = (previous != null)
                ? "上轮问题：" + previous
                : "无历史对话";

        String userPrompt = """
                对话历史：
                %s

                最新问题：%s

                改写查询：
                """.formatted(history, userMessage);

        String reply = deepSeekService.chat(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(userPrompt)
        ));

        List<String> queries = reply.lines()
                .map(String::strip)
                .filter(l -> !l.isEmpty())
                .map(l -> l.replaceAll("^\\d+[.、]\\s*", ""))
                .limit(6)
                .toList();

        log.info("原问题: {} → 改写: {}", userMessage, queries);
        return queries;
    }
}
