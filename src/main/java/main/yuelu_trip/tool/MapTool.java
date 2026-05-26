package main.yuelu_trip.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

@Component
public class MapTool {

    private static final String BASE_URL = "https://restapi.amap.com/v3";
    private static final String YUELU_COORDS = "112.944107,28.179271";

    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public MapTool(@Value("${amap.api-key}") String apiKey) {
        this.apiKey = apiKey;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = new ObjectMapper();
    }

    /** 规划前往岳麓山的路线 */
    public String getDirections(String origin, String mode) {
        try {
            String originCoords = geoCode(origin);
            if (originCoords == null) return "未找到起点：" + origin;

            String apiMode = switch (mode) {
                case "walking" -> "walking";
                case "transit" -> "transit/integrated";
                default -> "driving";
            };

            URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/direction/" + apiMode)
                    .queryParam("key", apiKey)
                    .queryParam("origin", originCoords)
                    .queryParam("destination", YUELU_COORDS)
                    .queryParam("city", "长沙")
                    .build().encode().toUri();

            String json = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(json);

            if (!"1".equals(root.get("status").asText())) {
                return "路线规划失败：" + root.get("info").asText()
                        + "，建议尝试其他出行方式（驾车/driving）";
            }

            JsonNode route = root.get("route");
            if (route == null || !route.has("paths") || route.get("paths").isEmpty()) {
                return "未找到从「" + origin + "」到岳麓山的" + modeLabel(mode) + "路线"
                        + "，建议尝试其他出行方式（驾车/driving 或步行/walking）";
            }

            JsonNode firstPath = route.get("paths").get(0);
            String distance = formatDistance(firstPath.get("distance").asDouble());
            String duration = formatDuration(firstPath.get("duration").asDouble());

            StringBuilder sb = new StringBuilder();
            sb.append("从「").append(origin).append("」到岳麓山（").append(modeLabel(mode)).append("）：\n");
            sb.append("距离").append(distance).append("，预计").append(duration).append("\n");

            if (firstPath.has("steps") && firstPath.get("steps").isArray()) {
                sb.append("\n路线指引：\n");
                int stepNum = 1;
                for (JsonNode step : firstPath.get("steps")) {
                    String instruction = step.get("instruction").asText();
                    sb.append(stepNum++).append(". ").append(instruction).append("\n");
                }
            }

            return sb.toString().strip();
        } catch (Exception e) {
            return "路线规划失败: " + e.getMessage();
        }
    }

    /** 搜索岳麓山附近的场所 */
    public String searchNearby(String keywords, String types) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/place/around")
                    .queryParam("key", apiKey)
                    .queryParam("location", YUELU_COORDS)
                    .queryParam("keywords", keywords)
                    .queryParam("radius", 1000)
                    .queryParam("offset", 10)
                    .queryParam("city", "长沙")
                    .build().encode().toUri();

            if (types != null && !types.isBlank()) {
                uri = UriComponentsBuilder.fromUri(uri)
                        .queryParam("types", types)
                        .build(true).toUri();
            }

            String json = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(json);

            if (!"1".equals(root.get("status").asText())) {
                return "搜索失败：" + root.get("info").asText();
            }

            JsonNode pois = root.get("pois");
            if (pois == null || !pois.isArray() || pois.isEmpty()) {
                return "岳麓山附近未找到「" + keywords + "」相关信息";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("岳麓山附近「").append(keywords).append("」搜索结果：\n");
            for (JsonNode poi : pois) {
                String name = poi.get("name").asText();
                String address = poi.has("address") ? poi.get("address").asText("") : "";
                String distance = poi.has("distance") ? formatDistance(poi.get("distance").asDouble()) : "";
                sb.append("\n· ").append(name);
                if (!address.isBlank()) sb.append("（").append(address).append("）");
                if (!distance.isBlank()) sb.append("【距山脚").append(distance).append("】");
            }

            return sb.toString().strip();
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
    }

    /** 地理编码：文字地址 → 经纬度 */
    private String geoCode(String address) throws Exception {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/geocode/geo")
                .queryParam("key", apiKey)
                .queryParam("address", address)
                .queryParam("city", "长沙")
                .build().encode().toUri();

        String json = restClient.get().uri(uri).retrieve().body(String.class);
        JsonNode root = objectMapper.readTree(json);

        if (!"1".equals(root.get("status").asText())) return null;

        JsonNode geocodes = root.get("geocodes");
        if (geocodes == null || !geocodes.isArray() || geocodes.isEmpty()) return null;

        return geocodes.get(0).get("location").asText();
    }

    private static String formatDistance(double meters) {
        if (meters < 1000) return String.format("%.0f米", meters);
        return String.format("%.1f公里", meters / 1000);
    }

    private static String formatDuration(double seconds) {
        int minutes = (int) Math.round(seconds / 60);
        if (minutes < 60) return minutes + "分钟";
        return (minutes / 60) + "小时" + (minutes % 60) + "分钟";
    }

    private static String modeLabel(String mode) {
        return switch (mode) {
            case "walking" -> "步行";
            case "transit" -> "公交";
            default -> "驾车";
        };
    }
}
