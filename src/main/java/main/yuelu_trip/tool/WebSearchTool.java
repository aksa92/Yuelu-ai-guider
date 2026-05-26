package main.yuelu_trip.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WebSearchTool {

    private static final String TAVILY_URL = "https://api.tavily.com/search";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public WebSearchTool(@Value("${tavily.api-key}") String apiKey) {
        this.apiKey = apiKey;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(15));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = new ObjectMapper();
    }

    public String search(String query) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("api_key", apiKey);
            body.put("query", query);
            body.put("search_depth", "basic");
            body.put("include_answer", true);
            body.put("max_results", 5);

            String json = restClient.post()
                    .uri(TAVILY_URL)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            StringBuilder sb = new StringBuilder();

            if (root.has("answer") && !root.get("answer").isNull()) {
                sb.append("摘要：").append(root.get("answer").asText()).append("\n\n");
            }

            JsonNode results = root.get("results");
            if (results != null && results.isArray()) {
                for (JsonNode r : results) {
                    sb.append(r.get("title").asText()).append("\n");
                    sb.append(r.get("content").asText()).append("\n");
                    sb.append("来源：").append(r.get("url").asText()).append("\n\n");
                }
            }

            String result = sb.toString().strip();
            return result.isEmpty() ? "未搜索到相关信息" : result;
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
    }
}
