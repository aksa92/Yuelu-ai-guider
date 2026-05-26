package main.yuelu_trip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import main.yuelu_trip.config.DeepSeekProperties;
import main.yuelu_trip.tool.WeatherTool;
import main.yuelu_trip.tool.WebSearchTool;
import main.yuelu_trip.tool.MapTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.function.Function;

@Service
public class DeepSeekService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekService.class);
    private static final int MAX_TOOL_DEPTH = 5;

    private final ChatModel chatModel;
    private final StreamingChatModel streamingModel;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DeepSeekProperties properties;
    private final Map<String, Function<Map<String, Object>, String>> toolExecutors;
    private final ObjectNode toolDefinitions;

    public DeepSeekService(DeepSeekProperties properties, WeatherTool weatherTool, WebSearchTool webSearchTool, MapTool mapTool) {
        this.properties = properties;
        String baseUrl = properties.getBaseUrl() + "/v1";

        this.chatModel = OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(baseUrl)
                .modelName(properties.getModel())
                .temperature(0.7)
                .build();

        this.streamingModel = OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(baseUrl)
                .modelName(properties.getModel())
                .temperature(0.7)
                .build();

        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl + "/chat/completions")
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();

        this.objectMapper = new ObjectMapper();

        // 工具定义（原生 JSON，避免 LangChain4j 序列化问题）
        this.toolDefinitions = objectMapper.createObjectNode(); // 用于后续扩展
        this.toolExecutors = Map.of(
                "getWeather", args -> {
                    String city = (String) args.get("city");
                    return weatherTool.getWeather(city);
                },
                "webSearch", args -> {
                    String query = (String) args.get("query");
                    return webSearchTool.search(query);
                },
                "getDirections", args -> {
                    String origin = (String) args.get("origin");
                    String mode = (String) args.getOrDefault("mode", "transit");
                    return mapTool.getDirections(origin, mode);
                },
                "searchNearby", args -> {
                    String keywords = (String) args.get("keywords");
                    String types = (String) args.getOrDefault("types", "");
                    return mapTool.searchNearby(keywords, types);
                }
        );
        log.info("已加载工具: [getWeather, webSearch, getDirections, searchNearby]");
    }

    public String chat(List<ChatMessage> messages) {
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .build();
        ChatResponse response = chatModel.chat(request);
        return response.aiMessage().text();
    }

    public String chatWithTools(List<ChatMessage> messages) {
        try {
            ArrayNode messagesArr = buildMessagesArray(messages);
            return chatWithToolsInternal(messagesArr, 0);
        } catch (Exception e) {
            log.error("chatWithTools 失败", e);
            return "抱歉，处理请求时出错：" + e.getMessage();
        }
    }

    private ArrayNode buildMessagesArray(List<ChatMessage> messages) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (ChatMessage msg : messages) {
            ObjectNode node = objectMapper.createObjectNode();
            if (msg instanceof dev.langchain4j.data.message.SystemMessage sysMsg) {
                node.put("role", "system");
                node.put("content", sysMsg.text());
            } else if (msg instanceof UserMessage userMsg) {
                node.put("role", "user");
                node.put("content", userMsg.singleText());
            } else if (msg instanceof ToolExecutionResultMessage toolMsg) {
                node.put("role", "tool");
                node.put("tool_call_id", toolMsg.id());
                node.put("content", toolMsg.text());
            } else {
                continue;
            }
            arr.add(node);
        }
        return arr;
    }

    private String chatWithToolsInternal(ArrayNode messagesArr, int depth) throws Exception {
        if (depth >= MAX_TOOL_DEPTH) {
            log.warn("工具调用达到最大深度 {}", MAX_TOOL_DEPTH);
            return "咨询次数已上限，请简要总结已有信息回答用户";
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("temperature", 0.7);
        body.set("messages", messagesArr);

        // 每次调用都传入工具定义
        body.set("tools", buildToolsArray());

        // 接近上限时给模型提示，让它收尾
        if (depth >= MAX_TOOL_DEPTH - 1) {
            ObjectNode hintMsg = objectMapper.createObjectNode();
            hintMsg.put("role", "user");
            hintMsg.put("content", "注意：你还能调一次工具，如果需要多次调用请优先选最重要的。结束后直接回答用户。");
            ((com.fasterxml.jackson.databind.node.ArrayNode) body.get("messages")).add(hintMsg);
        }

        String responseJson = restClient.post()
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);

        JsonNode response = objectMapper.readTree(responseJson);
        JsonNode choice = response.get("choices").get(0);
        JsonNode message = choice.get("message");

        // 记录 token 消耗（如有）
        JsonNode usage = response.get("usage");
        if (usage != null) {
            log.info("本轮消耗: {}tokens in + {}tokens out = {} total",
                    usage.get("prompt_tokens").asText(),
                    usage.get("completion_tokens").asText(),
                    usage.get("total_tokens").asText());
        }

        // 检查模型是否因内容过滤被截断
        String finishReason = choice.get("finish_reason").asText("");
        if ("content_filter".equals(finishReason)) {
            log.warn("内容被过滤");
            return "抱歉，内容被安全机制过滤，请换个方式提问";
        }

        JsonNode toolCalls = message.get("tool_calls");

        if (toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty()) {
            // 先加一次 assistant message（包含所有 tool_calls），再加对应的 tool 结果
            messagesArr.add(message);

            for (JsonNode tc : toolCalls) {
                String callId = tc.get("id").asText();
                String functionName = tc.get("function").get("name").asText();
                String arguments = tc.get("function").get("arguments").asText();

                log.info("工具调用[轮次{}]: {} args={}", depth + 1, functionName, arguments);

                long start = System.currentTimeMillis();
                String toolResult = executeTool(functionName, arguments);
                long elapsed = System.currentTimeMillis() - start;

                log.info("工具结果[{}ms]: {}", elapsed, toolResult);
                if (elapsed > 5000) {
                    log.warn("工具 {} 耗时过长: {}ms", functionName, elapsed);
                }

                ObjectNode toolMsg = objectMapper.createObjectNode();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", callId);
                toolMsg.put("content", toolResult);
                messagesArr.add(toolMsg);
            }
            return chatWithToolsInternal(messagesArr, depth + 1);
        }

        return message.get("content").asText("");
    }

    /** 构建 DeepSeek 工具定义 JSON */
    private ArrayNode buildToolsArray() {
        ArrayNode tools = objectMapper.createArrayNode();

        // getWeather 工具
        ObjectNode weatherTool = tools.addObject();
        weatherTool.put("type", "function");
        ObjectNode func = weatherTool.putObject("function");
        func.put("name", "getWeather");
        func.put("description", "获取长沙市（岳麓山所在地）的实时天气和未来3天天气预报");
        ObjectNode params = func.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");
        ObjectNode city = props.putObject("city");
        city.put("type", "string");
        city.put("description", "城市名称，仅限长沙（岳麓山所在城市）");
        params.putArray("required").add("city");

        // webSearch 工具
        ObjectNode searchTool = tools.addObject();
        searchTool.put("type", "function");
        ObjectNode searchFunc = searchTool.putObject("function");
        searchFunc.put("name", "webSearch");
        searchFunc.put("description", "搜索互联网获取岳麓山/长沙相关的实时信息，如开放时间、门票价格、交通状况、美食推荐、最新活动等");
        ObjectNode searchParams = searchFunc.putObject("parameters");
        searchParams.put("type", "object");
        ObjectNode searchProps = searchParams.putObject("properties");
        ObjectNode query = searchProps.putObject("query");
        query.put("type", "string");
        query.put("description", "搜索关键词，建议包含'岳麓山'或'长沙'以获取更准确结果");
        searchParams.putArray("required").add("query");

        // getDirections 工具
        ObjectNode dirTool = tools.addObject();
        dirTool.put("type", "function");
        ObjectNode dirFunc = dirTool.putObject("function");
        dirFunc.put("name", "getDirections");
        dirFunc.put("description", "规划前往岳麓山的路线，支持驾车、公交、步行三种方式。当游客问路怎么走时使用此工具");
        ObjectNode dirParams = dirFunc.putObject("parameters");
        dirParams.put("type", "object");
        ObjectNode dirProps = dirParams.putObject("properties");
        ObjectNode origin = dirProps.putObject("origin");
        origin.put("type", "string");
        origin.put("description", "起点位置，如'五一广场'、'长沙南站'、'湖南大学'");
        ObjectNode mode = dirProps.putObject("mode");
        mode.put("type", "string");
        mode.set("enum", objectMapper.createArrayNode()
                .add("driving").add("transit").add("walking"));
        mode.put("description", "出行方式：driving=驾车, transit=公交, walking=步行，默认公交");
        dirParams.putArray("required").add("origin");

        // searchNearby 工具
        ObjectNode nearbyTool = tools.addObject();
        nearbyTool.put("type", "function");
        ObjectNode nearbyFunc = nearbyTool.putObject("function");
        nearbyFunc.put("name", "searchNearby");
        nearbyFunc.put("description", "搜索岳麓山附近的餐厅、停车场、酒店、厕所等场所。当游客问周边有什么时使用");
        ObjectNode nearbyParams = nearbyFunc.putObject("parameters");
        nearbyParams.put("type", "object");
        ObjectNode nearbyProps = nearbyParams.putObject("properties");
        ObjectNode keywords = nearbyProps.putObject("keywords");
        keywords.put("type", "string");
        keywords.put("description", "搜索关键词，如'餐厅'、'停车场'、'洗手间'");
        ObjectNode types = nearbyProps.putObject("types");
        types.put("type", "string");
        types.put("description", "可选：场所分类，如050000=餐饮, 060000=购物, 150000=交通设施, 170000=生活服务");
        nearbyParams.putArray("required").add("keywords");

        return tools;
    }

    private String executeTool(String functionName, String arguments) {
        try {
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
            var executor = toolExecutors.get(functionName);
            if (executor == null) return "未知工具: " + functionName;
            return executor.apply(args);
        } catch (Exception e) {
            log.error("工具执行失败: {}", functionName, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    // -- stream --

    public void streamChat(List<ChatMessage> messages, SseEmitter emitter,
                           StringBuilder collector, Runnable onComplete) {
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .build();

        streamingModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                collector.append(partialResponse);
                try {
                    emitter.send(partialResponse);
                } catch (IOException e) {}
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                onComplete.run();
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                emitter.completeWithError(error);
            }
        });
    }
}
