package main.yuelu_trip.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/map")
public class MapController {

    private static final String BASE_URL = "https://restapi.amap.com/v3";
    private static final String YUELU_COORDS = "112.944107,28.179271";
    private static final String YUELU_NAME = "岳麓山";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public MapController(@Value("${amap.api-key}") String apiKey) {
        this.apiKey = apiKey;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/directions")
    public Map<String, Object> getDirections(@RequestBody Map<String, String> request) {
        String origin = request.get("origin");
        String mode = request.getOrDefault("mode", "driving");
        String originCoords = geoCode(origin);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("origin", origin);
        result.put("mode", mode);

        if (originCoords == null) {
            result.put("error", "未找到起点：" + origin);
            return result;
        }

        try {
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
                result.put("error", root.get("info").asText());
                return result;
            }

            result.put("originCoords", originCoords);
            result.put("destinationCoords", YUELU_COORDS);
            result.put("destinationName", YUELU_NAME);

            JsonNode route = root.get("route");
            if (route == null) {
                result.put("error", "未找到路线");
                return result;
            }

            if ("transit".equals(mode)) {
                parseTransitRoute(route, result);
            } else {
                parseDrivingRoute(route, result);
            }

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    private void parseDrivingRoute(JsonNode route, Map<String, Object> result) {
        JsonNode paths = route.get("paths");
        if (paths == null || !paths.isArray() || paths.isEmpty()) {
            result.put("error", "未找到驾车/步行路线");
            return;
        }

        JsonNode firstPath = paths.get(0);
        result.put("distance", firstPath.get("distance").asDouble());
        result.put("duration", firstPath.get("duration").asDouble());

        List<Map<String, Object>> steps = new ArrayList<>();
        List<double[]> allCoords = new ArrayList<>();

        for (JsonNode step : firstPath.get("steps")) {
            Map<String, Object> stepMap = new LinkedHashMap<>();
            stepMap.put("instruction", step.get("instruction").asText());

            String polylineStr = step.has("polyline") ? step.get("polyline").asText() : "";
            List<double[]> coords = parsePolyline(polylineStr);
            stepMap.put("polyline", coords);
            steps.add(stepMap);
            allCoords.addAll(coords);
        }

        result.put("steps", steps);
        result.put("routeCoords", allCoords);
    }

    private void parseTransitRoute(JsonNode route, Map<String, Object> result) {
        JsonNode transits = route.get("transits");
        if (transits == null || !transits.isArray() || transits.isEmpty()) {
            result.put("error", "未找到公交路线");
            return;
        }

        JsonNode firstTransit = transits.get(0);
        result.put("distance", firstTransit.get("distance").asDouble());
        // transit duration is in minutes, convert to seconds for consistency
        double durationMin = firstTransit.has("duration") ? firstTransit.get("duration").asDouble() : 0;
        result.put("duration", durationMin * 60);

        List<Map<String, Object>> segments = new ArrayList<>();
        List<double[]> allCoords = new ArrayList<>();

        for (JsonNode seg : firstTransit.get("segments")) {
            // walking segment
            if (seg.has("walking")) {
                JsonNode walking = seg.get("walking");
                for (JsonNode step : walking.get("steps")) {
                    Map<String, Object> stepMap = new LinkedHashMap<>();
                    stepMap.put("instruction", step.get("instruction").asText());
                    String polylineStr = step.has("polyline") ? step.get("polyline").asText() : "";
                    List<double[]> coords = parsePolyline(polylineStr);
                    stepMap.put("polyline", coords);
                    segments.add(stepMap);
                    allCoords.addAll(coords);
                }
            }
            // bus segment
            if (seg.has("bus")) {
                JsonNode bus = seg.get("bus");
                for (JsonNode busLine : bus) {
                    Map<String, Object> stepMap = new LinkedHashMap<>();
                    String lineName = busLine.has("name") ? busLine.get("name").asText() : "";
                    String depStop = busLine.has("departure_stop") ? busLine.get("departure_stop").get("name").asText() : "";
                    String arrStop = busLine.has("arrival_stop") ? busLine.get("arrival_stop").get("name").asText() : "";
                    stepMap.put("instruction", "乘坐" + lineName + "（" + depStop + "→" + arrStop + "）");
                    // bus lines have via_stops with coordinates
                    List<double[]> coords = new ArrayList<>();
                    if (busLine.has("via_stops")) {
                        for (JsonNode vs : busLine.get("via_stops")) {
                            String loc = vs.get("location").asText();
                            coords.add(parseCoord(loc));
                        }
                    }
                    stepMap.put("polyline", coords);
                    segments.add(stepMap);
                    allCoords.addAll(coords);
                }
            }
            // subway/railway segment (similar to bus)
            if (seg.has("subway")) {
                JsonNode subway = seg.get("subway");
                for (JsonNode line : subway) {
                    Map<String, Object> stepMap = new LinkedHashMap<>();
                    String lineName = line.has("name") ? line.get("name").asText() : "";
                    String depStop = line.has("departure_stop") ? line.get("departure_stop").get("name").asText() : "";
                    String arrStop = line.has("arrival_stop") ? line.get("arrival_stop").get("name").asText() : "";
                    stepMap.put("instruction", "乘坐" + lineName + "（" + depStop + "→" + arrStop + "）");
                    List<double[]> coords = new ArrayList<>();
                    if (line.has("via_stops")) {
                        for (JsonNode vs : line.get("via_stops")) {
                            String loc = vs.get("location").asText();
                            coords.add(parseCoord(loc));
                        }
                    }
                    stepMap.put("polyline", coords);
                    segments.add(stepMap);
                    allCoords.addAll(coords);
                }
            }
        }

        result.put("steps", segments);
        result.put("routeCoords", allCoords);
    }

    /** 解析高德 polyline 字符串 "x1,y1;x2,y2;..." */
    private List<double[]> parsePolyline(String polyline) {
        List<double[]> coords = new ArrayList<>();
        if (polyline == null || polyline.isBlank()) return coords;
        for (String point : polyline.split(";")) {
            coords.add(parseCoord(point));
        }
        return coords;
    }

    /** 解析 "lng,lat" → [lng, lat] */
    private double[] parseCoord(String point) {
        String[] parts = point.split(",");
        return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
    }

    @GetMapping("/search")
    public Map<String, Object> searchPoi(@RequestParam String keywords) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/place/text")
                    .queryParam("key", apiKey)
                    .queryParam("keywords", keywords)
                    .queryParam("city", "长沙")
                    .queryParam("offset", 5)
                    .build().encode().toUri();

            String json = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(json);

            if (!"1".equals(root.get("status").asText())) {
                result.put("error", root.get("info").asText());
                return result;
            }

            List<Map<String, Object>> pois = new ArrayList<>();
            for (JsonNode poi : root.get("pois")) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", poi.get("name").asText());
                item.put("address", poi.get("address").asText());
                item.put("location", poi.get("location").asText());
                item.put("distance", poi.get("distance").asText());
                pois.add(item);
            }
            result.put("pois", pois);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    /** 地理编码：文字地址 → "lng,lat" */
    private String geoCode(String address) {
        try {
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
        } catch (Exception e) {
            return null;
        }
    }
}
