# Execution Report

执行时间：2026-04-25

## 已完成

- 已将优化后的部署方案归档到文档目录：`docs/deployment-plan.md`
- 已创建后端 FastAPI 项目结构。
- 已创建前端 React/Vite 项目结构。
- 已创建本地存储目录：`data/recordings`、`data/normalized`、`data/transcripts`、`data/summaries`。
- 已创建模型缓存目录：`models/funasr`。
- 已创建日志目录：`logs`。
- 已安装后端依赖到 `backend/.venv`。
- 已安装 FunASR、PyTorch CPU、torchaudio。
- 已下载 Node.js 24.14.0 到工作区 `.tools/node-v24.14.0-win-x64`。
- 已复制 ffmpeg 8.1 到工作区 `.tools/ffmpeg/ffmpeg.exe`。
- 已触发 FunASR 模型下载，缓存到 `models/funasr`。
- 已安装前端依赖并完成生产构建。
- 已启动后端和前端静态服务。

## 关键调整

- PyTorch 最新 `2.11.0+cpu` 在当前 Windows 环境导入 `c10.dll` 失败，已固定为 `torch==2.5.1+cpu` 和 `torchaudio==2.5.1+cpu`。
- Winget 安装 Node.js 因源更新网络错误失败，已改为下载官方 Node.js Windows zip 到工作区使用。
- Winget 安装目录下的 ffmpeg 在当前沙箱中访问受限，已复制到工作区 `.tools/ffmpeg`。
- Vite dev server 在当前环境触发 `spawn EPERM`，已改用 `npm run build` 后由 Python 静态服务器托管 `frontend/dist`。
- FunASR 模型别名离线重载仍会访问 ModelScope API，已将 `.env` 中的 ASR/VAD/标点模型改为工作区本地模型路径。

## 验证结果

- 后端健康检查：`http://127.0.0.1:8000/health`
- 健康检查返回：`status=ok`、`python=3.12.13`、`ffmpeg=true`、`funasr=true`、`asr_model=paraformer-zh`
- 录音列表接口：`http://127.0.0.1:8000/api/recordings` 可访问
- 上传冒烟测试：生成 1 秒 `smoke-test.wav` 并通过 `POST /api/recordings` 上传成功
- 详情接口：`GET /api/recordings/{id}` 可返回录音元数据、空转写、空摘要和空任务列表
- 模型下载：已下载 ASR 主模型、VAD 模型、标点模型，共约 2.09GB
- 模型加载：本地路径 `AutoModel(...)` 重载成功
- 后端配置：`/health` 已确认 `asr_model` 指向本地模型目录
- 云端 LLM：已新增 `LLM_PROVIDER=mimo`，默认使用 `https://token-plan-cn.xiaomimimo.com/v1` 和 `MiMo-V2.5`
- 云端 LLM：已新增 `MIMO_API_KEY` 与 `MIMO_THINKING` 配置，OpenAI SDK 使用 Bearer 鉴权路径
- MIMO 试运行：后端已切换到 `LLM_PROVIDER=mimo` 并重启，`/health` 返回 `llm_base_url=https://token-plan-cn.xiaomimimo.com/v1`、`llm_model=MiMo-V2.5`
- MIMO 试运行：当前未配置 `MIMO_API_KEY`，摘要服务按预期返回 `MIMO_API_KEY or LLM_API_KEY is not configured.`
- ASR 回归修复：FunASR 原始输出仅包含完整 `text` 和逐字 `timestamp`，已新增按中文标点切句并映射时间戳的后处理。
- ASR 回归修复：重跑 `2026年04月08日 16点09分.m4a` 后，转写段从 `1826` 个空洞段修正为 `59` 个非空句段。
- ASR 回归修复：录音时长已正确写入 `350.2293125` 秒。
- 前端设置入口：已新增 LLM 设置面板，可选择 `mimo`、`deepseek`、`tongyi`、`qwen`，输入 API Key，保存配置并测试连通性。
- 环境检测：侧栏健康状态已新增 LLM provider、模型、API Key 配置状态和连通性测试结果。
- 后端设置接口：已新增 `GET /api/settings/llm`、`PUT /api/settings/llm`、`POST /api/settings/llm/test`，FastAPI TestClient 验证通过。
- 前端页面：`http://127.0.0.1:8000/` 返回 HTTP 200
- 前端构建：`npm run build` 成功

## 当前限制

- `LLM_API_KEY` 尚未配置，因此摘要接口会在调用时提示未配置密钥。
- 首次真实转写时 FunASR 会下载模型，耗时取决于网络和模型大小。
- 当前尚未用真实音频跑完 ASR 任务；服务和依赖已验证到可启动、可导入、可访问。

## 常用命令

```powershell
.\setup.ps1
.\start-all.ps1
.\stop-all.ps1
```
