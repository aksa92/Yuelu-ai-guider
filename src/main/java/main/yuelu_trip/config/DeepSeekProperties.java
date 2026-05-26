package main.yuelu_trip.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {
    private String apiKey;
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-v4-flash";
    private String embeddingBaseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private String embeddingApiKey;
    private String embeddingModel = "embedding-3";
}
