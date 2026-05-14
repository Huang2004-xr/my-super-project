package com.example.agentplatform.service;

import com.example.agentplatform.dto.AiProviderPresetResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiProviderPresetService {
    private final List<AiProviderPresetResponse> presets = Arrays.asList(
            preset("custom-openai-compatible", "Custom OpenAI-compatible", "CUSTOM",
                    "openai_chat_completions", "Authorization", "", "", null,
                    "适合第三方中转站，Base URL 和模型由用户填写。", aliases(""), Arrays.asList()),
            preset("deepseek", "DeepSeek", "CN", "openai_chat_completions", "Authorization",
                    "https://api.deepseek.com", "deepseek-chat", "https://platform.deepseek.com",
                    "国内 OpenAI-compatible 接入。", aliases("deepseek-chat"),
                    Arrays.asList("deepseek-chat", "deepseek-reasoner")),
            preset("qwen", "Qwen / 通义千问", "CN", "openai_chat_completions", "Authorization",
                    "https://dashscope.aliyuncs.com/compatible-mode", "qwen-plus", "https://dashscope.aliyun.com",
                    "通义千问 OpenAI-compatible 接入。", aliases("qwen-plus"),
                    Arrays.asList("qwen-plus", "qwen-turbo", "qwen-max", "qwen3-coder-plus")),
            preset("kimi", "Kimi", "CN", "openai_chat_completions", "Authorization",
                    "https://api.moonshot.cn", "moonshot-v1-8k", "https://platform.moonshot.cn",
                    "Moonshot/Kimi OpenAI-compatible 接入。", aliases("moonshot-v1-8k"),
                    Arrays.asList("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")),
            preset("zhipu", "智谱 GLM", "CN", "openai_chat_completions", "Authorization",
                    "https://open.bigmodel.cn/api/paas", "glm-4-flash", "https://open.bigmodel.cn",
                    "智谱 GLM OpenAI-compatible 接入。", aliases("glm-4-flash"),
                    Arrays.asList("glm-4-flash", "glm-4-plus", "glm-4-air")),
            preset("minimax", "MiniMax", "CN", "openai_chat_completions", "Authorization",
                    "https://api.minimax.chat", "MiniMax-Text-01", "https://platform.minimaxi.com",
                    "MiniMax OpenAI-compatible 接入。", aliases("MiniMax-Text-01"),
                    Arrays.asList("MiniMax-Text-01", "abab6.5s-chat")),
            preset("mimo-token-plan-cn", "MiMo Token Plan CN", "CN", "anthropic_messages", "x-api-key",
                    "https://token-plan-cn.xiaomimimo.com/anthropic", "mimo-v2.5-pro",
                    "https://pi.dev/models/xiaomi-token-plan-cn/mimo-v2-5-pro",
                    "MiMo Token Plan 通常使用 Anthropic Messages 协议和小写模型 id。", aliases("mimo-v2.5-pro"),
                    Arrays.asList("mimo-v2.5-pro")),
            preset("openai", "OpenAI", "GLOBAL", "openai_chat_completions", "Authorization",
                    "https://api.openai.com", "gpt-4o-mini", "https://platform.openai.com",
                    "OpenAI 官方接口。", aliases("gpt-4o-mini"),
                    Arrays.asList("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini")),
            preset("anthropic", "Anthropic", "GLOBAL", "anthropic_messages", "x-api-key",
                    "https://api.anthropic.com", "claude-3-5-haiku-latest", "https://console.anthropic.com",
                    "Anthropic Messages 官方接口。", aliases("claude-3-5-haiku-latest"),
                    Arrays.asList("claude-3-5-haiku-latest", "claude-3-5-sonnet-latest", "claude-3-7-sonnet-latest")),
            preset("openrouter", "OpenRouter", "GLOBAL", "openai_chat_completions", "Authorization",
                    "https://openrouter.ai/api", "openai/gpt-4o-mini", "https://openrouter.ai",
                    "OpenRouter OpenAI-compatible 接入。", aliases("openai/gpt-4o-mini"),
                    Arrays.asList("openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet", "google/gemini-2.0-flash-001"))
    );

    public List<AiProviderPresetResponse> list() {
        return presets;
    }

    public AiProviderPresetResponse find(String key) {
        return presets.stream()
                .filter(item -> item.key.equals(key))
                .findFirst()
                .orElse(null);
    }

    private static AiProviderPresetResponse preset(String key, String label, String region, String apiFormat,
            String authHeaderName, String baseUrl, String defaultModel, String officialUrl,
            String remark, Map<String, String> modelAliases, List<String> modelOptions) {
        return new AiProviderPresetResponse(key, label, region, apiFormat, authHeaderName, baseUrl,
                defaultModel, officialUrl, remark, modelAliases, modelOptions);
    }

    private static Map<String, String> aliases(String model) {
        Map<String, String> aliases = new LinkedHashMap<>();
        if (model != null && !model.trim().isEmpty()) {
            aliases.put("main", model);
            aliases.put("chat", model);
            aliases.put("imagePrompt", model);
            aliases.put("videoScript", model);
            aliases.put("knowledge", model);
            aliases.put("fast", model);
            aliases.put("strong", model);
        }
        return aliases;
    }
}
