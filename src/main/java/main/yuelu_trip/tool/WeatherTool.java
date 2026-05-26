package main.yuelu_trip.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

@Component
public class WeatherTool {

    private final RestClient restClient;
    private final String apiKey;
    private final String apiHost;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private final Map<String, String> cityCache = new ConcurrentHashMap<>(Map.of(
            "长沙", "101250101",
            "北京", "101010100",
            "上海", "101020100",
            "广州", "101280101",
            "深圳", "101280601",
            "成都", "101270101",
            "武汉", "101200101",
            "杭州", "101210101",
            "西安", "101110101",
            "重庆", "101040100"
    ));

    public WeatherTool(@Value("${weather.api-key}") String apiKey,
                       @Value("${weather.api-host}") String apiHost) {
        this.apiKey = apiKey;
        this.apiHost = apiHost;
        this.restClient = RestClient.builder()
                .defaultHeader("X-QW-Api-Key", apiKey)
                .build();
    }

    @Tool("获取指定城市的实时天气和未来3天天气预报")
    public String getWeather(@P("城市名称，例如：北京、长沙、上海") String city) {
        String locationId = cityCache.computeIfAbsent(city, this::lookupCityId);
        if (locationId == null) return "未找到城市：" + city;

        try {
            String nowJson = fetch("https://%s/v7/weather/now?location=%s".formatted(apiHost, locationId));
            var now = objectMapper.readValue(nowJson, WeatherNowResponse.class);
            if (!"200".equals(now.code())) return city + "天气数据获取失败";

            String forecastJson = fetch("https://%s/v7/weather/3d?location=%s".formatted(apiHost, locationId));
            var forecast = objectMapper.readValue(forecastJson, WeatherForecastResponse.class);

            return formatWeather(city, now, forecast);
        } catch (Exception e) {
            return "获取天气失败: " + e.getMessage();
        }
    }

    private String fetch(String url) throws Exception {
        byte[] raw = restClient.get().uri(url).retrieve().body(byte[].class);
        if (raw == null) return "{}";
        // 和风天气 API 使用 gzip 压缩
        if (raw.length > 2 && raw[0] == (byte) 0x1f && raw[1] == (byte) 0x8b) {
            try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                return new String(gzip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return new String(raw, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String lookupCityId(String city) {
        try {
            var geo = restClient.get()
                    .uri("https://geoapi.qweather.com/v2/city/lookup?location={city}", city)
                    .retrieve()
                    .body(GeoResponse.class);
            if (geo != null && geo.location() != null && !geo.location().isEmpty()) {
                return geo.location().get(0).id();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String formatWeather(String city, WeatherNowResponse now, WeatherForecastResponse forecast) {
        var n = now.now();
        StringBuilder sb = new StringBuilder();
        sb.append(city).append("当前天气：")
                .append(n.text()).append("，")
                .append("温度").append(n.temp()).append("℃，")
                .append(n.windDir()).append(n.windScale()).append("级");

        if (forecast != null && forecast.daily() != null && !forecast.daily().isEmpty()) {
            sb.append("\n");
            for (var day : forecast.daily()) {
                sb.append(day.fxDate()).append("：")
                        .append(day.textDay()).append("，")
                        .append(day.tempMin()).append("~").append(day.tempMax()).append("℃")
                        .append("\n");
            }
        }

        return sb.toString().strip();
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WeatherNowResponse(String code, Now now) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Now(String temp, String text, String windDir, String windScale, String humidity) {}
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WeatherForecastResponse(String code, java.util.List<Daily> daily) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Daily(String fxDate, String tempMax, String tempMin, String textDay, String windDirDay) {}
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoResponse(String code, java.util.List<Location> location) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Location(String name, String id, String adm1) {}
    }
}
