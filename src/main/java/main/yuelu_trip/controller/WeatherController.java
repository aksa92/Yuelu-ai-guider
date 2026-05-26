package main.yuelu_trip.controller;

import main.yuelu_trip.tool.WeatherTool;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherTool weatherTool;

    public WeatherController(WeatherTool weatherTool) {
        this.weatherTool = weatherTool;
    }

    @GetMapping
    public Map<String, Object> getWeather(@RequestParam(defaultValue = "长沙") String city) {
        String text = weatherTool.getWeather(city);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("raw", text);

        // 解析结构化数据返回给前端
        try {
            String[] lines = text.split("\n");
            String firstLine = lines[0];
            // "长沙当前天气：晴，温度28℃，南风3级"
            String temp = "";
            String condition = "";
            String wind = "";

            var tempMatcher = java.util.regex.Pattern.compile("(\\d+)℃").matcher(firstLine);
            if (tempMatcher.find()) temp = tempMatcher.group(1);

            var condMatcher = java.util.regex.Pattern.compile("天气：([^，]+)").matcher(firstLine);
            if (condMatcher.find()) condition = condMatcher.group(1);

            var windMatcher = java.util.regex.Pattern.compile("([东南西北]+风\\d+级)").matcher(firstLine);
            if (windMatcher.find()) wind = windMatcher.group(1);

            result.put("temp", temp);
            result.put("text", condition);
            result.put("wind", wind);

            // 解析预报：2026-05-25：晴，22~30℃
            java.util.List<Map<String, String>> forecast = new java.util.ArrayList<>();
            String[] dayLabels = {"今天", "明天", "后天"};
            for (int i = 1; i < lines.length && i - 1 < dayLabels.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                Map<String, String> day = new LinkedHashMap<>();
                day.put("day", dayLabels[i - 1]);

                var dayCondMatcher = java.util.regex.Pattern.compile("：([^，]+)").matcher(line);
                if (dayCondMatcher.find()) day.put("text", dayCondMatcher.group(1));

                var tempRangeMatcher = java.util.regex.Pattern.compile("(\\d+)~(\\d+)℃").matcher(line);
                if (tempRangeMatcher.find()) {
                    day.put("low", tempRangeMatcher.group(1));
                    day.put("high", tempRangeMatcher.group(2));
                }
                forecast.add(day);
            }
            result.put("forecast", forecast);
        } catch (Exception e) {
            // 解析失败时保留 raw 文本，前端降级显示
            result.put("parseError", e.getMessage());
        }

        return result;
    }
}
