package main.yuelu_trip.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class Bm25Service {

    private static final Logger log = LoggerFactory.getLogger(Bm25Service.class);
    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final VectorService vectorService;

    /** BM25 index: for each doc, map of term → count */
    private final List<Map<String, Integer>> docTermFreqs = new ArrayList<>();
    /** Number of docs containing each term */
    private final Map<String, Integer> docFreq = new HashMap<>();
    private int totalDocs = 0;
    private double avgDocLength = 0;

    public Bm25Service(VectorService vectorService) {
        this.vectorService = vectorService;
    }

    @PostConstruct
    public void init() {
        List<VectorService.Chunk> chunks = vectorService.getAllChunks();
        totalDocs = chunks.size();
        if (totalDocs == 0) return;

        int totalTerms = 0;
        for (VectorService.Chunk chunk : chunks) {
            Map<String, Integer> tf = termFreq(chunk.text());
            docTermFreqs.add(tf);
            totalTerms += tf.values().stream().mapToInt(Integer::intValue).sum();

            for (String term : tf.keySet()) {
                docFreq.merge(term, 1, Integer::sum);
            }
        }
        avgDocLength = (double) totalTerms / totalDocs;
        log.info("BM25 索引完成: {} 个文档, 词典 {} 个词元, 平均长度 {}", totalDocs, docFreq.size(), String.format("%.1f", avgDocLength));
    }

    public List<ScoredDoc> search(String query, int topK) {
        if (totalDocs == 0) return List.of();

        Map<String, Integer> queryTerms = termFreq(query);
        double[] scores = new double[totalDocs];

        for (int i = 0; i < totalDocs; i++) {
            Map<String, Integer> docTf = docTermFreqs.get(i);
            double docLen = docTf.values().stream().mapToInt(Integer::intValue).sum();
            double score = 0;

            for (String term : queryTerms.keySet()) {
                Integer tf = docTf.get(term);
                if (tf == null) continue;

                Integer df = docFreq.get(term);
                if (df == null || df == 0) continue;

                double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);
                score += idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * docLen / avgDocLength));
            }
            scores[i] = score;
        }

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < totalDocs; i++) indices.add(i);
        indices.sort((a, b) -> Double.compare(scores[b], scores[a]));

        List<ScoredDoc> results = new ArrayList<>();
        List<VectorService.Chunk> allChunks = vectorService.getAllChunks();
        for (int i = 0; i < Math.min(topK, totalDocs); i++) {
            int idx = indices.get(i);
            if (scores[idx] > 0) {
                results.add(new ScoredDoc(allChunks.get(idx), scores[idx]));
            }
        }
        return results;
    }

    /** 简单中文分词：字符二元组（bigram） */
    private Map<String, Integer> termFreq(String text) {
        Map<String, Integer> freq = new HashMap<>();
        String cleaned = text.replaceAll("[\\s,，。！？、；：'（）()\\n\\r\\t]", "").toLowerCase();
        for (int i = 0; i < cleaned.length(); i++) {
            // unigram
            freq.merge(String.valueOf(cleaned.charAt(i)), 1, Integer::sum);
            // bigram
            if (i + 1 < cleaned.length()) {
                freq.merge(cleaned.substring(i, i + 2), 1, Integer::sum);
            }
        }
        return freq;
    }

    public record ScoredDoc(VectorService.Chunk chunk, double score) {}
}
