package main.yuelu_trip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class RagEvaluator {

    private static final int MAX_RECORDS = 100;

    private final List<EvalRecord> records = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public RagEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record EvalRecord(
            String question,
            List<String> retrievedChunks,
            List<String> chunkSources,
            String answer,
            LocalDateTime time
    ) {}

    public void record(String question, List<VectorService.Chunk> chunks, String answer) {
        records.add(new EvalRecord(
                question,
                chunks.stream().map(VectorService.Chunk::text).toList(),
                chunks.stream().map(VectorService.Chunk::source).distinct().toList(),
                answer,
                LocalDateTime.now()
        ));
        while (records.size() > MAX_RECORDS) {
            records.remove(0);
        }
    }

    public String generateReport() {
        if (records.isEmpty()) return "暂无评估数据";

        long total = records.size();
        Set<String> allSources = records.stream()
                .flatMap(r -> r.chunkSources().stream())
                .collect(Collectors.toSet());

        Map<String, Long> sourceUsage = records.stream()
                .flatMap(r -> r.chunkSources().stream())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        StringBuilder report = new StringBuilder();
        report.append("========== RAG 评估报告 ==========\n");
        report.append("总问答次数: ").append(total).append("\n");
        report.append("知识来源数: ").append(allSources.size()).append(" (").append(String.join(", ", allSources)).append(")\n\n");
        report.append("来源使用频率:\n");
        sourceUsage.forEach((source, count) ->
                report.append("  ").append(source).append(": ").append(count).append(" 次 (").append(String.format("%.0f%%", count * 100.0 / total)).append(")\n"));
        report.append("\n最近 5 条记录:\n");
        records.subList(Math.max(0, records.size() - 5), records.size())
                .forEach(r -> {
                    report.append("  Q: ").append(truncate(r.question(), 50)).append("\n");
                    report.append("  来源: ").append(r.chunkSources()).append("\n");
                    report.append("  A: ").append(truncate(r.answer(), 80)).append("\n\n");
                });
        return report.toString();
    }

    public int totalRecords() {
        return records.size();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
