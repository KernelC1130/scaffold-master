package com.scaffold.vuln.ai;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.credential.BearerTokenCredential;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.system.service.SystemManagementService;
import com.scaffold.vuln.ai.VulnTools;
import com.scaffold.vuln.ai.WeatherTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.stereotype.Component;

/**
 * AI 模型动态装配工厂：模型服务三参数存于 sys_config（ai.model.*），
 * 管理员改配置保存后下次对话即用新配置，无需重启；
 * 兼容任意 OpenAI 协议端点（官方 API / 代理服务 / 私有化部署）
 */
@Component
public class AiModelFactory {

    /** 对话请求超时（推理模型生成较慢） */
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(120);

    /** 连通性测试超时（要求快速失败） */
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(30);

    /** sys_config 参数键 */
    public static final String KEY_BASE_URL = "ai.model.base-url";
    public static final String KEY_API_KEY = "ai.model.api-key";
    public static final String KEY_MODEL = "ai.model.name";

    /** SQL 预置的占位值，视为未配置 */
    private static final String PLACEHOLDER_KEY = "sk-在此填写";

    private static final String SYSTEM_PROMPT = "你是漏洞管理平台的智能助手。"
            + "- 涉及平台业务数据（漏洞/工单/资产/统计）时，调用对应工具查询，不凭记忆编造\n"
            + "- 通用技术问题与一般性常识问题，直接正常回答，不必调用工具\n"
            + "- 回答保持简洁专业";

    private final SystemManagementService configService;
    private final WeatherTools weatherTools;
    private final VulnTools vulnTools;

    public AiModelFactory(SystemManagementService configService, WeatherTools weatherTools, VulnTools vulnTools) {
        this.configService = configService;
        this.weatherTools = weatherTools;
        this.vulnTools = vulnTools;
    }

    /**
     * 获取当前配置对应的 ChatClient（每次重建：构建只是本地对象拼接无 IO，
     * 管理员改配置后下一问即生效，无需失效通知）
     */
    public ChatClient getChatClient() {
        String today = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE").withLocale(Locale.CHINA));
        return ChatClient.builder(buildChatModel(CHAT_TIMEOUT))
                .defaultSystem("今天是 " + today + "。\n" + SYSTEM_PROMPT)
                .defaultTools(vulnTools, weatherTools)
                .build();
    }

    /**
     * 连通性测试：用当前已保存配置临时构建短超时客户端发一句 ping，
     * 按错误类型给出可操作的提示
     */
    public String testConnection() {
        OpenAiChatModel chatModel = buildChatModel(TEST_TIMEOUT);
        try {
            ChatResponse response = chatModel.call(new org.springframework.ai.chat.prompt.Prompt("ping"));
            String text = response.getResult().getOutput().getText();
            return text == null || text.isBlank() ? "连通成功（模型返回空内容）" : text;
        } catch (UnauthorizedException e) {
            throw new BusinessException("鉴权失败：API Key 无效或已过期（401）");
        } catch (NotFoundException e) {
            throw new BusinessException("端点或模型不存在（404）：请检查服务端点是否带 /v1、模型名是否正确");
        } catch (PermissionDeniedException e) {
            throw new BusinessException("访问被拒绝（403）：该 Key 无权访问此模型");
        } catch (RateLimitException e) {
            throw new BusinessException("触发限流或额度不足（429）");
        } catch (OpenAIIoException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("timed out") || msg.contains("timeout")) {
                throw new BusinessException("连接超时：服务端点无响应，请检查地址或网络");
            }
            throw new BusinessException("连接失败：无法访问服务端点（" + e.getMessage() + "）");
        } catch (OpenAIException e) {
            throw new BusinessException("服务端返回异常：" + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException("测试失败：" + e.getMessage());
        }
    }

    /** 按 sys_config 当前配置构建 ChatModel */
    private OpenAiChatModel buildChatModel(Duration timeout) {
        String baseUrl = trimToEmpty(configService.configValueByKey(KEY_BASE_URL));
        String apiKey = trimToEmpty(configService.configValueByKey(KEY_API_KEY));
        String model = trimToEmpty(configService.configValueByKey(KEY_MODEL));
        if (baseUrl.isEmpty() || model.isEmpty() || apiKey.isEmpty() || PLACEHOLDER_KEY.equals(apiKey)) {
            throw new BusinessException("AI 模型未配置，请到【系统管理 - 参数配置】完成配置");
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        ClientOptions options = ClientOptions.builder()
                .baseUrl(baseUrl)
                // openai-java 4.x 凭据体系：apiKey(String) 在 build 时会被忽略，必须显式给 credential
                .credential(BearerTokenCredential.create(apiKey))
                .timeout(timeout)
                .maxRetries(0)
                .httpClient(SpringAiOpenAiHttpClient.builder().build())
                .build();
        // sync / async 都要显式传入：build() 会懒构建 async 客户端，
        // 不传时它内部重新走空凭据的 ClientOptions 而报 credential 缺失
        OpenAIClientImpl client = new OpenAIClientImpl(options);
        return OpenAiChatModel.builder()
                .openAiClient(client)
                .openAiClientAsync(client.async())
                .options(OpenAiChatOptions.builder().model(model).build())
                .build();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
