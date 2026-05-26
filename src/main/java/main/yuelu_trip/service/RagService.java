package main.yuelu_trip.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final int TOP_K = 3;
    private static final int RRF_K = 60;

    private final VectorService vectorService;
    private final Bm25Service bm25Service;
    private final DeepSeekService deepSeekService;
    private final RagEvaluator evaluator;
    private final QueryRewriter queryRewriter;

    public RagService(VectorService vectorService, Bm25Service bm25Service, DeepSeekService deepSeekService,
                      RagEvaluator evaluator, QueryRewriter queryRewriter) {
        this.vectorService = vectorService;
        this.bm25Service = bm25Service;
        this.deepSeekService = deepSeekService;
        this.evaluator = evaluator;
        this.queryRewriter = queryRewriter;
    }

    public String chat(String userMessage, String conversationId, ChatMemory memory) {
        // 查询改写：1个问题 → 3个检索词 → 分别检索 → 合并去重
        List<String> queries = queryRewriter.rewrite(userMessage, conversationId);
        Set<VectorService.Chunk> merged = new LinkedHashSet<>();
        for (String q : queries) {
            // 向量检索
            List<VectorService.Chunk> vectorResults = vectorService.search(q, TOP_K);
            // BM25 关键词检索
            List<Bm25Service.ScoredDoc> bm25Results = bm25Service.search(q, TOP_K);
            // RRF 融合：向量 + BM25
            List<VectorService.Chunk> fused = fuse(vectorResults, bm25Results, q);
            merged.addAll(fused);
        }
        int mergedLimit = Math.max(TOP_K, queries.size());
        List<VectorService.Chunk> relevant = merged.stream().limit(mergedLimit).toList();

        String context = relevant.stream()
                .map(c -> {
                    String tag = c.metadata().getOrDefault("category", c.source());
                    return "【" + tag + "】" + c.text();
                })
                .collect(Collectors.joining("\n\n"));

        String augmentedMessage = """
                请基于以下资料回答用户的问题。如果资料不足以回答问题，请如实说不知道。

                参考资料：
                %s

                用户问题：%s
                """.formatted(context, userMessage);

        memory.add(new dev.langchain4j.data.message.UserMessage(augmentedMessage));
        String reply = deepSeekService.chatWithTools(memory.messages());

        memory.add(new dev.langchain4j.data.message.AiMessage(reply));

        // 把 原问题+检索结果+回答 记录到评估日志
        evaluator.record(userMessage, relevant, reply);

        return reply;
    }

    /** 用 RRF（倒数排序融合）合并向量检索和 BM25 检索结果 */
    private List<VectorService.Chunk> fuse(List<VectorService.Chunk> vectorResults,
                                            List<Bm25Service.ScoredDoc> bm25Results,
                                            String query) {
        Map<VectorService.Chunk, Double> rrfScores = new HashMap<>();

        for (int rank = 0; rank < bm25Results.size(); rank++) {
            VectorService.Chunk chunk = bm25Results.get(rank).chunk();
            rrfScores.put(chunk, 1.0 / (RRF_K + rank + 1));
        }

        for (int rank = 0; rank < vectorResults.size(); rank++) {
            VectorService.Chunk chunk = vectorResults.get(rank);
            rrfScores.merge(chunk, 1.0 / (RRF_K + rank + 1), Double::sum);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<VectorService.Chunk, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
