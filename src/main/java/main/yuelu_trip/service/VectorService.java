package main.yuelu_trip.service;

import jakarta.annotation.PostConstruct;
import main.yuelu_trip.config.DeepSeekProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VectorService {

    private static final Logger log = LoggerFactory.getLogger(VectorService.class);

    private final List<Chunk> chunks = new ArrayList<>();
    private final RestClient restClient;
    private final String embeddingModel;

    public record Chunk(String text, String source, float[] vector, Map<String, String> metadata) {

        public double similarity(float[] other) {
            double dot = 0, normA = 0, normB = 0;
            for (int i = 0; i < vector.length; i++) {
                dot += (double) vector[i] * other[i];
                normA += (double) vector[i] * vector[i];
                normB += (double) other[i] * other[i];
            }
            return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Chunk chunk)) return false;
            return Objects.equals(text, chunk.text);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(text);
        }
    }

    public VectorService(DeepSeekProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getEmbeddingBaseUrl() + "/embeddings")
                .defaultHeader("Authorization", "Bearer " + properties.getEmbeddingApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.embeddingModel = properties.getEmbeddingModel();
    }

    @PostConstruct
    public void init() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:knowledge/*.txt");

        for (Resource resource : resources) {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String source = resource.getFilename();
            splitAndIndex(content, source);
        }

        log.info("知识库加载完成: {} 个片段", chunks.size());
    }

    private void splitAndIndex(String content, String source) {
        Map<String, String> metadata = new HashMap<>(Map.of("source", source != null ? source : "unknown"));
        String body = parseFrontmatter(content, metadata);

        String[] paragraphs = body.split("\n{2,}");
        int idx = 0;
        for (String para : paragraphs) {
            String text = para.strip();
            if (text.isEmpty() || text.startsWith("#")) continue;
            float[] vector = embed(text);
            metadata.put("index", String.valueOf(idx++));
            chunks.add(new Chunk(text, source, vector, new HashMap<>(metadata)));
        }
    }

    /** 解析 --- 包裹的 frontmatter（简易版，只支持 key: value 一行一个） */
    private static String parseFrontmatter(String content, Map<String, String> target) {
        if (!content.startsWith("---")) return content;
        int end = content.indexOf("---", 3);
        if (end == -1) return content;

        for (String line : content.substring(3, end).stripIndent().lines().toList()) {
            line = line.strip();
            if (line.isBlank() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).strip();
                String val = line.substring(colon + 1).strip().replaceAll("^[\"']|[\"']$", "");
                target.put(key, val);
            }
        }
        return content.substring(end + 3).strip();
    }

    private float[] embed(String text) {
        var request = Map.of("model", embeddingModel, "input", text);
        EmbeddingResponse response = restClient.post()
                .body(request)
                .retrieve()
                .body(EmbeddingResponse.class);

        List<Double> raw = response.data().get(0).embedding();
        float[] vec = new float[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            vec[i] = raw.get(i).floatValue();
        }
        return vec;
    }

    public List<Chunk> search(String query, int topK) {
        float[] queryVec = embed(query);

        return chunks.stream()
                .map(c -> new AbstractMap.SimpleEntry<>(c, c.similarity(queryVec)))
                .sorted(Map.Entry.<Chunk, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<Chunk> getAllChunks() {
        return Collections.unmodifiableList(chunks);
    }

    // -- API response DTOs --

    @SuppressWarnings("unused")
    private record EmbeddingResponse(List<EmbeddingData> data) {}

    @SuppressWarnings("unused")
    private record EmbeddingData(List<Double> embedding) {}
}
