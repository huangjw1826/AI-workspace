# Cloud LLM Providers

后端摘要服务统一使用 OpenAI Python SDK 的 `client.chat.completions.create(...)`。

## Provider Presets

| Provider | Base URL | Default Model | API Key |
| --- | --- | --- | --- |
| `deepseek` | `https://api.deepseek.com` | `deepseek-chat` | `LLM_API_KEY` |
| `tongyi` / `qwen` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` | `LLM_API_KEY` |
| `mimo` | `https://token-plan-cn.xiaomimimo.com/v1` | `MiMo-V2.5` | `MIMO_API_KEY` or `LLM_API_KEY` |

## Xiaomi MiMo

官方文档：`https://platform.xiaomimimo.com/docs/api/chat/openai-api`

已确认参数：

- Chat Completions endpoint: `https://api.xiaomimimo.com/v1/chat/completions`
- OpenAI SDK base URL: `https://api.xiaomimimo.com/v1`
- Authentication: `api-key: $MIMO_API_KEY` or `Authorization: Bearer $MIMO_API_KEY`
- Recommended example model: `mimo-v2.5-pro`
- Available model IDs include `mimo-v2.5-pro`, `mimo-v2.5`, `mimo-v2-pro`, `mimo-v2-omni`, `mimo-v2-flash`
- Thinking control: `thinking: {"type": "enabled" | "disabled"}`

Current implementation uses the OpenAI SDK bearer-token path. The project default uses the Token Plan CN endpoint requested for this deployment:

- Base URL: `https://token-plan-cn.xiaomimimo.com/v1`
- Model: `MiMo-V2.5`

```env
LLM_PROVIDER=mimo
MIMO_API_KEY=your_mimo_api_key
MIMO_THINKING=disabled
LLM_MAX_COMPLETION_TOKENS=2048
```

`LLM_BASE_URL`, `LLM_MODEL`, `LLM_TEMPERATURE`, and `LLM_TOP_P` can override provider defaults when needed.
