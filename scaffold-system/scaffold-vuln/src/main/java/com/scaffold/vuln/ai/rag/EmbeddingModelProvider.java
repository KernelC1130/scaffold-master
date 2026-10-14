package com.scaffold.vuln.ai.rag;

import java.time.Duration;
import java.util.Map;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.credential.BearerTokenCredential;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.system.service.SystemManagementService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.stereotype.Component;

/**
 * 向量模型（Embedding）动态装配：三参数存于 sys_config（ai.embedding.*），
 * 独立于对话模型配置。端点为 OpenAI 协议兼容（默认指向硅基流动）
 */
@Component
public class EmbeddingModelProvider {

    private static final Duration EMBED_TIMEOUT = Duration.ofSeconds(30);

    public static final String KEY_BASE_URL = "ai.embedding.base-url";
    public static final String KEY_API_KEY = "ai.embedding.api-key";
    public static final String KEY_MODEL = "ai.embedding.model";

    private static final String PLACEHOLDER_KEY = "sk-在此填写";

    private final SystemManagementService configService;

    /** 已装配的 EmbeddingModel 缓存 */
    private volatile EmbeddingModel cachedModel;

    public EmbeddingModelProvider(SystemManagementService configService) {
        this.configService = configService;
    }

    /** 获取当前配置对应的 EmbeddingModel（带缓存） */
    public EmbeddingModel getEmbeddingModel() {
        EmbeddingModel model = cachedModel;
        if (model == null) {
            synchronized (this) {
                if (cachedModel == null) {
                    cachedModel = buildEmbeddingModel();
                }
                model = cachedModel;
            }
        }
        return model;
    }

    /** 配置变更后失效缓存 */
    public void evict() {
        cachedModel = null;
    }

    /** 连通性测试：发一个词的 embed 请求，返回向量维度与耗时；异常转为可操作的提示 */
    public Map<String, Object> testEmbedding() {
        long t = System.currentTimeMillis();
        try {
            float[] vec = getEmbeddingModel().embed("ping");
            long ms = System.currentTimeMillis() - t;
            return Map.of("ok", true, "msg", "向量维度 " + vec.length, "ms", ms);
        } catch (BusinessException e) {
            return Map.of("ok", false, "msg", e.getMessage(), "ms", System.currentTimeMillis() - t);
        } catch (Exception e) {
            return Map.of("ok", false, "msg", "测试失败：" + e.getMessage(), "ms", System.currentTimeMillis() - t);
        }
    }

    /** 读取向量模型相关参数值（供管线记录当前模型名等） */
    public String getConfigValue(String key) {
        return configService.configValueByKey(key);
    }

    /** 按 sys_config 当前配置构建 EmbeddingModel */
    private EmbeddingModel buildEmbeddingModel() {
        String baseUrl = trimToEmpty(configService.configValueByKey(KEY_BASE_URL));
        String apiKey = trimToEmpty(configService.configValueByKey(KEY_API_KEY));
        String model = trimToEmpty(configService.configValueByKey(KEY_MODEL));
        if (baseUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty() || PLACEHOLDER_KEY.equals(apiKey)) {
            throw new BusinessException("AI 向量模型未配置，请到【系统管理 - 参数配置】完成配置");
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        ClientOptions options = ClientOptions.builder()
                .baseUrl(baseUrl)
                .credential(BearerTokenCredential.create(apiKey))
                .timeout(EMBED_TIMEOUT)
                .maxRetries(0)
                .httpClient(SpringAiOpenAiHttpClient.builder().build())
                .build();
        return OpenAiEmbeddingModel.builder()
                .openAiClient(new OpenAIClientImpl(options))
                .options(OpenAiEmbeddingOptions.builder().model(model).build())
                .build();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
