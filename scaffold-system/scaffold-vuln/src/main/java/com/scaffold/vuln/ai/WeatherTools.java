package com.scaffold.vuln.ai;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.system.service.SystemManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 实时天气工具（和风天气，OpenAI 协议无关——独立 HTTP 调用）：
 * GeoAPI 城市查询 -> /v7/weather/now 实时天气；
 * 鉴权走和风新版 X-QW-Api-Key 头；key 与 API Host 存于 sys_config（weather.*）；
 * 任何失败返回「天气服务暂不可用」，不向模型抛异常
 */
@Component
public class WeatherTools {

    private static final Logger log = LoggerFactory.getLogger(WeatherTools.class);

    public static final String KEY_API_KEY = "weather.api-key";
    public static final String KEY_API_HOST = "weather.api-host";

    /** 连接与请求超时均 3 秒 */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private final SystemManagementService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherTools(SystemManagementService configService) {
        this.configService = configService;
    }

    /** 查询城市实时天气（GeoAPI 定位城市 -> weather/now 实况 -> 中文概要） */
    @Tool(description = "查询指定城市的实时天气（温度、体感、天气现象、风力、湿度）。用户询问天气相关问题时调用；同一问题涉及多个城市时，分别调用多次")
    public String getWeather(@ToolParam(description = "城市名称，如：广州、北京") String city) {
        try {
            String key = trimToEmpty(configService.configValueByKey(KEY_API_KEY));
            String host = trimToEmpty(configService.configValueByKey(KEY_API_HOST));
            if (key.isEmpty() || host.isEmpty() || "在此填写".equals(key)) {
                return "天气服务暂不可用（未配置和风天气 Key 或 API Host）";
            }
            if (host.endsWith("/")) {
                host = host.substring(0, host.length() - 1);
            }

            String locationId = lookupCityId(city, key, host);
            if (locationId == null) {
                return "未找到城市「" + city + "」的天气数据";
            }

            JsonNode resp = getJson(host + "/v7/weather/now?location=" + locationId, key, "now");
            if (resp == null) {
                return "天气服务暂不可用";
            }
            // 和风 /v7/weather/now 的实况数据嵌在响应的 now 字段下，根对象只有 code/updateTime
            JsonNode now = resp.get("now");
            if (now == null) {
                return "天气服务暂不可用";
            }
            return city + " 当前 " + now.path("temp").asText() + "°C（" + now.path("text").asText()
                    + "），体感 " + now.path("feelsLike").asText() + "°C，"
                    + now.path("windDir").asText() + " " + now.path("windScale").asText() + " 级，湿度 "
                    + now.path("humidity").asText() + "%";
        } catch (Exception e) {
            log.warn("[AI-天气] 查询失败：{}", e.getMessage());
            return "天气服务暂不可用";
        }
    }

    /** GeoAPI 城市查询，返回首个匹配城市的 LocationID；未匹配返回 null */
    private String lookupCityId(String city, String key, String host) throws Exception {
        JsonNode resp = getJson(host + "/geo/v2/city/lookup?location="
                + URLEncoder.encode(city, StandardCharsets.UTF_8), key, "location");
        if (resp == null) {
            return null;
        }
        JsonNode locations = resp.get("location");
        return locations == null || !locations.isArray() || locations.isEmpty()
                ? null : locations.get(0).path("id").asText();
    }

    /** GET 请求并解析 JSON；按和风约定校验 code=200 且存在 expect 字段 */
    private JsonNode getJson(String url, String key, String expect) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("X-QW-Api-Key", key)
                    .GET()
                    .build();
            // 和风 CDN 强制 gzip 压缩响应，JDK HttpClient 不会自动解压（BodyHandlers.ofString
            // 拿到的是压缩字节），必须按 Content-Encoding 手动解压
            HttpResponse<java.io.InputStream> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            java.io.InputStream in = resp.body();
            if ("gzip".equalsIgnoreCase(resp.headers().firstValue("Content-Encoding").orElse(""))) {
                in = new java.util.zip.GZIPInputStream(in);
            }
            JsonNode json = objectMapper.readTree(in);
            if (!"200".equals(json.path("code").asText()) || json.get(expect) == null) {
                JsonNode error = json.get("error");
                log.info("[AI-天气] 响应未成功：{}", truncate(error != null ? error.path("detail").asText() : json.toString(), 120));
                return null;
            }
            return json;
        } catch (Exception e) {
            log.warn("[AI-天气] 请求失败：{}（{}）", url, e.getMessage());
            return null;
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
}
